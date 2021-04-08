package ru.tecon.server;

import ru.tecon.ProjectProperty;
import ru.tecon.Utils;
import ru.tecon.controllerData.ControllerConfig;
import ru.tecon.exception.MyServerStartException;
import ru.tecon.instantData.InstantDataService;
import ru.tecon.jms.MessageReceiveService;
import ru.tecon.traffic.BlockType;
import ru.tecon.traffic.Event;
import ru.tecon.traffic.Statistic;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Сервер для приема socket сообщений от MFK-1500
 * реализация протокола pushEvents
 */
public class EchoSocketServer {

    private static Logger log = Logger.getLogger(EchoSocketServer.class.getName());

    private static final MessageReceiveService JMS_SERVICE = new MessageReceiveService();

    private static ConcurrentMap<String, Statistic> statistic = new ConcurrentHashMap<>();

    private static ServerSocket serverSocket;

    private static ScheduledExecutorService service;
    private static ScheduledFuture dayFuture;
    private static ScheduledFuture hourFuture;
    private static ScheduledFuture future20Minute;

    private static boolean closeApplication = true;

    private static Event event;

    public static void main(String[] args) {
        try {
            LogManager.getLogManager().readConfiguration(EchoSocketServer.class.getResourceAsStream("/log.properties"));
            startService(args);
        } catch (IOException e) {
            log.log(Level.WARNING, "load logging config error:", e);
        } catch (MyServerStartException e) {
            log.log(Level.WARNING, "server start exception:", e);
        }
    }

    public static void startService(String... args) throws MyServerStartException {
        ProjectProperty.loadProperties(args[0]);
        log.info("project properties load");
        System.out.println("Конфигурация приложения загружена");

        // Парсим файл конфигурации
        try {
            if (!ControllerConfig.parsControllerConfig()) {
                Utils.error("error controller config load. Parse error");
            }
        } catch (IOException e) {
            Utils.error("error controller config load message:", e);
        }
        log.info("controller config load");
        System.out.println("Конфигурация контроллера загружена");

        File[] files = new File(ProjectProperty.getStatisticSerFolder()).listFiles();
        if (files != null) {
            for (File fileEntry: files) {
                if (!fileEntry.isDirectory()) {
                    String ip = fileEntry.getName().replaceFirst("[.][^.]+$", "").replaceAll("_", ".");
                    statistic.put(ip, deserialize(fileEntry.toPath().toAbsolutePath().toString()));
                }
            }
        }

        // Догружаю в статистику объекты, которые ранее передавали данные (по именам папок логов pushEvent)
        // Требуется для защиты в случае утери логов.
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(ProjectProperty.getPushEventLogFolder()),
                entry -> !statistic.keySet().contains(entry.getFileName().toString()))) {
            stream.forEach(path -> getStatistic(path.getFileName().toString()).update());
        } catch (IOException e) {
            log.warning("error load statistic from pushEvent logs directory " + e.getMessage());
        }
        log.info("controller config load");
        System.out.println("Конфигурация контроллера загружена");

        // Подключаемся к jms через JMS_SERVICE
        JMS_SERVICE.initService();
        log.info("jms service start");
        System.out.println("Сервер получения данных доступен");

        ControllerConfig.startUploaderService();
        log.info("controller config service start");
        System.out.println("Сервис обработки запросов конфигурации контроллера запущен");

        InstantDataService.startService();
        log.info("Instant data service start");
        System.out.println("Сервис обработки запросов на мгновенные данные запущен");

        long midnight= LocalDateTime.now().until(LocalDate.now().plusDays(1).atStartOfDay(), ChronoUnit.MINUTES) + 1;
        long nextHour = LocalDateTime.now().until(LocalDateTime.now().plusHours(1).withMinute(0), ChronoUnit.MINUTES);

        service = Executors.newScheduledThreadPool(3);

        dayFuture = service.scheduleAtFixedRate(() ->
                statistic.forEach((s, st) -> {
                    st.close();
                    st.clearSocketCount();
                    st.clearDayTraffic();
                    if (LocalDate.now().getDayOfMonth() == 1) {
                        st.clearMonthTraffic();
                    }
                    st.updateObjectName();
                    st.unblockAll();

                    // Удаляем файл с последними переданными группами данных
                    try {
                        Files.deleteIfExists(Paths.get(ProjectProperty.getPushEventLogFolder() +
                                "/" + s + "/" + ProjectProperty.PUSH_EVENT_LAST_CONFIG));
                    } catch (IOException e) {
                        log.warning("error remove last config file for " + s);
                    }

                    // Удаление файлов логов pushEvent старше 30 дней
                    try (Stream<Path> stream = Files.walk(Paths.get(ProjectProperty.getPushEventLogFolder()))
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
                                log.warning("error delete file " + path);
                            }
                        });
                    } catch (IOException e) {
                        log.warning("error walk files");
                    }
                }), midnight, TimeUnit.DAYS.toMinutes(1), TimeUnit.MINUTES);

        // Сервис для резервирования логов статистики раз в час
        hourFuture = service.scheduleAtFixedRate(() -> statistic.forEach((k, v) -> serialize(v)),
                nextHour, TimeUnit.HOURS.toMinutes(1), TimeUnit.MINUTES);

        // Сервис для автоматического снятия блокировок Ошибка сервера и Разрыв соединения. Раз в 20 минут.
        future20Minute = service.scheduleAtFixedRate(() -> statistic.forEach((k, v) -> v.unblock(BlockType.SERVER_ERROR, BlockType.LINK_ERROR)),
                nextHour + 5, 20, TimeUnit.MINUTES);

        log.info("Statistic service start");
        System.out.println("Сервис статистики запущен");

        // Запускаем serverSocket
        Socket socket;

        try {
            serverSocket = new ServerSocket(ProjectProperty.getPort(), 100);
        } catch (IOException e) {
            Utils.error("error start server socket Message:", e);
        }

        log.info("server socket start");
        System.out.println("Сервис приема данных по PushEvent запущен");
        System.out.println("Сервер MFK1500 запущен");

        while (!serverSocket.isClosed()) {
            try {
                socket = Objects.requireNonNull(serverSocket).accept();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    log.log(Level.WARNING, "error create socket connection Message:", e);
                } else {
                    log.info("Server socket is closed");
                }
                continue;
            }

            String host = socket.getInetAddress().getHostAddress();
            log.info("new connection from " + host);

            if (!isBlocked(host) && !getStatistic(host).isSocketOpen()) {
                EchoThread thread = new EchoThread(socket, ProjectProperty.getServerName());
                thread.start();

                log.info("Thread create " + thread.getId() + " for ip: " + host);
            }
        }
    }

    /**
     * Метод останавливает работу приложения
     */
    public static void stopSocket() {
        JMS_SERVICE.stopService();
        ControllerConfig.stopUploaderService();
        InstantDataService.stopService();

        if (Objects.nonNull(service)) {
            dayFuture.cancel(true);
            hourFuture.cancel(true);
            future20Minute.cancel(true);
            service.shutdown();
        }

        try {
            if (serverSocket != null) {
                serverSocket.close();
                statistic.forEach((k, v) -> {
                    v.close();
                    serialize(v);
                });
                statistic.clear();
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "error with close sockets:", e);
        }

        log.info("EchoSocketServer stop");
        System.out.println("Сервер MFK1500 остановлен");
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
        statistic.remove(ip);

        // Удаление всех pushEvent логов
        try (Stream<Path> walk = Files.walk(Paths.get(ProjectProperty.getPushEventLogFolder() + "/" + ip))) {
            walk.sorted(Comparator.reverseOrder())
                    .peek(path -> log.info("remove " + path))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warning("error remove file: " + path + " message: " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warning("error remove push event logs for ip: " + ip + " message: " + e.getMessage());
        }

        event.update();
    }

    public static void setEvent(Event event) {
        EchoSocketServer.event = event;
    }

    public static boolean isCloseApplication() {
        return closeApplication;
    }

    public static void setCloseApplication(boolean closeApplication) {
        EchoSocketServer.closeApplication = closeApplication;
    }

    /**
     * Сериализация объекта статистики в файл
     * @param statistic объект для сериализации
     */
    private static void serialize(Statistic statistic) {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(ProjectProperty.getStatisticSerFolder() + "/" + statistic.getIp().replaceAll("[.]", "_") + ".ser"))) {
            oos.writeObject(statistic);
        } catch (IOException e) {
            log.log(Level.WARNING, "serialize error", e);
        }
    }

    /**
     * Десиреализация объекта статистики из файла
     * @param path путь к файлу
     * @return объект статистики или null если ошибка десиреализации
     */
    private static Statistic deserialize(String path) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path))) {

            Statistic statistic = (Statistic) ois.readObject();
            statistic.setEvent(event);
            event.addItem(statistic);
            statistic.update();

            return statistic;
        } catch (IOException | ClassNotFoundException e) {
            log.log(Level.WARNING, "deserialize error", e);
        }
        return null;
    }
}
