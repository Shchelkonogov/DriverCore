package ru.tecon.server;

import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tecon.controllerData.ControllerConfig;
import ru.tecon.mfk1500Server.DriverProperty;
import ru.tecon.mfk1500Server.message.MessageService;
import ru.tecon.exception.MyServerStartException;
import ru.tecon.instantData.InstantDataService;
import ru.tecon.traffic.BlockType;
import ru.tecon.traffic.Event;
import ru.tecon.traffic.Statistic;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * Сервер для приема socket сообщений от MFK-1500
 * реализация протокола pushEvents
 */
public class EchoSocketServer {

    private static Logger logger = LoggerFactory.getLogger(EchoSocketServer.class);

    private static ConcurrentMap<String, Statistic> statistic = new ConcurrentHashMap<>();

    private static ServerSocket serverSocket;

    private static ScheduledExecutorService service;
    private static ScheduledFuture future20Minute;

    private static Event event;
    private static ServiceLoadListener serviceLoadListener;

    public static void main(String[] args) {
        try {
            startService();
        } catch (MyServerStartException e) {
            logger.warn("error start driver server", e);
        }
    }

    public static void startService() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//            try {
//                Thread.sleep(200);
                logger.info("Shutting down...");
                stopSocket();
                logger.info("Shutting down ok");
                LogManager.shutdown();
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                logger.warn("error shutdown hook", e);
//            }
        }));

        DriverProperty driverProperty = DriverProperty.getInstance();
        logger.info("driver properties loaded {}", driverProperty);

        if (serviceLoadListener != null) {
            serviceLoadListener.onLoad();
        }

        // Парсим файл конфигурации
        try {
            if (!ControllerConfig.parsControllerConfig()) {
                throw new MyServerStartException("controller config loading error");
            }
        } catch (IOException e) {
            throw new MyServerStartException("controller config loading error", e);
        }
        logger.info("controller config loaded");

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(DriverProperty.getInstance().getStatisticSerPath(),
                entry -> Files.isRegularFile(entry) && entry.toString().endsWith(".ser"))) {
            stream.forEach(path -> {
                try {
                    statistic.put(path.getFileName().toString().replaceFirst("[.][^.]+$", "").replaceAll("_", "."),
                            deserialize(path).orElseThrow(NoSuchElementException::new));
                } catch (NoSuchElementException ex) {
                    logger.warn("error deserialize object {}", path, ex);
                }
            });
        } catch (IOException ex) {
            logger.warn("error deserialize", ex);
        }

        // Догружаю в статистику объекты, которые ранее передавали данные (по именам папок логов pushEvent)
        // Требуется для защиты в случае утери логов.
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(DriverProperty.getInstance().getPushEventLogPath(),
                entry -> !statistic.keySet().contains(entry.getFileName().toString()))) {
            stream.forEach(path -> getStatistic(path.getFileName().toString()).update());
        } catch (IOException e) {
            logger.warn("error load statistic from pushEvent logs directory", e);
        }

        MessageService.startService();
        logger.info("Message service start");

        // Подключаемся к jms через JMS_SERVICE
//        JMS_SERVICE.initService();
//        logger.info("jms service start");

        ControllerConfig.startUploaderService();
        logger.info("controller config service start");

        InstantDataService.startService();
        logger.info("Instant data service start");

        // Определяем сколько минут осталось до ближайщего 20 минутного интервала (00/20/40 минут)
        long untilNextTime = 21;
        for (int i = 0; untilNextTime >= 20; i++) {
            untilNextTime = LocalDateTime.now().until(
                    LocalDateTime.now()
                            .plusHours(1)
                            .withMinute(0)
                            .minusMinutes(i * 20), ChronoUnit.MINUTES);
        }

        service = Executors.newSingleThreadScheduledExecutor();
        future20Minute = service.scheduleAtFixedRate(() -> {
            boolean triggerHour = false;
            boolean triggerDay = false;

            logger.info("Removing of blocks triggers every 20 minutes");
            if (LocalTime.now().getMinute() == 0) {
                logger.info("Reserving of statistic logs triggers every hour");
                triggerHour = true;
                if (LocalTime.now().getHour() == 0) {
                    logger.info("Updating of statistics triggers every day");
                    triggerDay = true;
                }
            }

            for (Map.Entry<String, Statistic> entry: statistic.entrySet()) {
                String k = entry.getKey();
                Statistic st = entry.getValue();

                // Автоматическое снятие блокировок Ошибка сервера и Разрыв соединения.
                // Срабатывает раз в 20 минут.
                st.unblock(BlockType.SERVER_ERROR, BlockType.LINK_ERROR);

                // Резервирование логов статистики.
                // Срабатывает раз в час.
                if (triggerHour) {
                    serialize(st);
                    if (st.isSocketHung()) {
                        logger.info("close hung socket for {}", k);
                        st.close();
                        st.update();
                    }
                }

                // Ежедневное обновление статистики и файлов логов.
                // Срабатывает в начале каждого дня.
                if (triggerDay) {
                    st.close();
                    st.clearSocketCount();
                    st.clearLastDayDataGroups();
                    st.clearDayTraffic();
                    if (LocalDate.now().getDayOfMonth() == 1) {
                        st.clearMonthTraffic();
                    }
                    st.updateObjectName();
                    st.unblockAll();

                    Path pushEventPath = DriverProperty.getInstance().getPushEventLogPath().resolve(k);

                    // Удаляем файл с последними переданными группами данных
                    try {
                        Files.deleteIfExists(pushEventPath.resolve(DriverProperty.getInstance().getPushEventLastConfig()));
                    } catch (IOException e) {
                        logger.warn("error remove last config file for", e);
                    }

                    // Удаление файлов логов pushEvent старше 30 дней
                    if (Files.exists(pushEventPath)) {
                        try (Stream<Path> stream = Files.walk(pushEventPath)
                                .filter(path -> {
                                    if (Files.isRegularFile(path) && (path.toString().endsWith(".txt"))) {
                                        try {
                                            FileTime creationTime = (FileTime) Files.getAttribute(path, "lastModifiedTime");

                                            return LocalDateTime.ofInstant(creationTime.toInstant(), ZoneId.systemDefault())
                                                    .isBefore(LocalDateTime.now().minusDays(30));
                                        } catch (IOException e) {
                                            return false;
                                        }
                                    } else {
                                        return false;
                                    }
                                })) {
                            stream.forEach(path -> {
                                try {
                                    Files.delete(path);
                                } catch (IOException e) {
                                    logger.warn("error delete file {}", path, e);
                                }
                            });
                        } catch (IOException e) {
                            logger.warn("error walk files {}", pushEventPath, e);
                        }
                    }
                }
            }
        }, untilNextTime, 20, TimeUnit.MINUTES);
        logger.info("Statistic service start");

        // Запускаем serverSocket
        Socket socket;

        try {
            serverSocket = new ServerSocket(DriverProperty.getInstance().getListeningPort(), 100);
        } catch (IOException e) {
            logger.warn("error start server socket", e);
            throw new MyServerStartException("error start server socket", e);
        }

        logger.info("server socket start");
        logger.info("MFK1500 server started");

        while (!serverSocket.isClosed()) {
            try {
                socket = Objects.requireNonNull(serverSocket).accept();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    logger.warn("error create socket connection", e);
                } else {
                    logger.warn("Server socket is closed");
                }
                continue;
            }

            String host = socket.getInetAddress().getHostAddress();

            if (!isBlocked(host) && !getStatistic(host).isSocketOpen()) {
                EchoThread thread = new EchoThread(socket, DriverProperty.getInstance().getServerName());
                thread.start();

                logger.info("Thread create {} for ip {}", thread.getId(), host);
            } else {
                logger.info("Connection {} ignore", host);
            }
        }
    }

    /**
     * Метод останавливает работу приложения
     */
    public static void stopSocket() {
//        JMS_SERVICE.stopService();
        ControllerConfig.stopUploaderService();
        InstantDataService.stopService();
        MessageService.stopService();

        if (Objects.nonNull(service)) {
            future20Minute.cancel(true);
            service.shutdown();
        }

        try {
            if (serverSocket != null) {
                serverSocket.close();
                statistic.forEach((k, v) -> {
                    logger.info("Serialize {}", k);
                    v.close();
                    serialize(v);
                });
                statistic.clear();
            }
        } catch (IOException e) {
            logger.warn("Error with close sockets", e);
        }

        logger.info("MFK1500 server stop");
    }

    public static Statistic getStatistic(String ip) {
        statistic.putIfAbsent(ip, new Statistic(ip, event));
        return statistic.get(ip);
    }

    private static boolean isBlocked(String ip) {
        return isBlocked(ip, null);
    }

    public static boolean isBlocked(String ip, BlockType blockType) {
        Statistic st = statistic.get(ip);
        return ((st != null) && st.isBlock(blockType));
    }

    public static ConcurrentMap<String, Statistic> getStatistic() {
        return statistic;
    }

    /**
     * Метод удаляет объект из статистики по его ip
     * @param ip ip объекта статистики для удаления
     */
    public static void removeStatistic(String ip) {
        logger.info("remove object {}", ip);

        // Удаление статистики из памяти
        statistic.remove(ip);

        // Удаление файла .ser
        try {
            Files.deleteIfExists(DriverProperty.getInstance().getStatisticSerPath().resolve(ip.replaceAll("[.]", "_") + ".ser"));
        } catch (IOException e) {
            logger.warn("can't remove .ser file", e);
        }

        // Удаление всех pushEvent логов
        try (Stream<Path> walk = Files.walk(DriverProperty.getInstance().getPushEventLogPath().resolve(ip))) {
            walk.sorted(Comparator.reverseOrder())
                    .peek(path -> logger.info("remove {}", path))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            logger.warn("Error remove file {}", path, e);
                        }
                    });
        } catch (NoSuchFileException ignore) {
        } catch (IOException e) {
            logger.warn("Error remove pushEvent logs for ip {}", ip, e);
        }

        if (event != null) {
            event.update();
        }
    }

    public static void setEvent(Event event) {
        EchoSocketServer.event = event;
    }

    /**
     * Сериализация объекта статистики в файл
     * @param statistic объект для сериализации
     */
    private static void serialize(Statistic statistic) {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                Files.newOutputStream(DriverProperty.getInstance().getStatisticSerPath().resolve(statistic.getIp().replaceAll("[.]", "_") + ".ser")))) {
            oos.writeObject(statistic);
        } catch (IOException e) {
            logger.warn("Serialize error", e);
        }
    }

    /**
     * Десиреализация объекта статистики из файла
     * @param path путь к файлу
     * @return объект статистики или null если ошибка десиреализации
     */
    private static Optional<Statistic> deserialize(Path path) {
        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(path))) {

            Statistic statistic = (Statistic) ois.readObject();

            if (event != null) {
                statistic.setEvent(event);
                event.addItem(statistic);
            }

            statistic.update();

            return Optional.of(statistic);
        } catch (IOException | ClassNotFoundException e) {
            logger.warn("deserialize error", e);
        }
        return Optional.empty();
    }

    public static void addServiceLoadListener(ServiceLoadListener serviceLoadListener) {
        EchoSocketServer.serviceLoadListener = serviceLoadListener;
    }
}
