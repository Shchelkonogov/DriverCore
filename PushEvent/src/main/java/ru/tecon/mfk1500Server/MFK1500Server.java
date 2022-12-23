package ru.tecon.mfk1500Server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.util.NettyRuntime;
import io.netty.util.internal.SystemPropertyUtil;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tecon.controllerData.ControllerConfig;
import ru.tecon.exception.MyServerStartException;
import ru.tecon.instantData.InstantDataService;
import ru.tecon.mfk1500Server.handler.CommandHandler;
import ru.tecon.mfk1500Server.handler.IgnoreHandler;
import ru.tecon.mfk1500Server.handler.MyChannelTrafficShapingHandler;
import ru.tecon.mfk1500Server.handler.PushEventHandler;
import ru.tecon.mfk1500Server.message.MessageService;
import ru.tecon.server.ServiceLoadListener;
import ru.tecon.traffic.BlockType;
import ru.tecon.traffic.Event;
import ru.tecon.traffic.Statistic;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

// TODO сделать статистику служб (хранить где-то последнее время работы и по запросу отправлять на морду,
//  а оттуда дать возможность перезапускать службу)
/**
 * @author Maksim Shchelkonogov
 */
public class MFK1500Server {

    private static Logger logger = LoggerFactory.getLogger(MFK1500Server.class);

    private static ConcurrentMap<String, Statistic> statistic = new ConcurrentHashMap<>();

    private static List<ChannelFuture> channelFutureList = new ArrayList<>();

    public static final ScheduledExecutorService WORKER_SERVICE = Executors.newScheduledThreadPool(3, new MyDefaultThreadFactory("MFK1500Server"));

    private static ServiceLoadListener serverLoadListener;
    private static Event clientChangeListener;

    private static NioEventLoopGroup bossGroup;
    private static NioEventLoopGroup workerGroup;

    public static void main(String[] args) {
        try {
            startServer();
        } catch (MyServerStartException e) {
            logger.warn("error start driver server", e);
            stopServer();
        }
    }

    /**
     * Метод запускает работу сервера MFK1500
     * @throws MyServerStartException если произошла ошибка запуска сервера MFK1500
     */
    public static void startServer() throws MyServerStartException {
        // Добавил обработку shutdownHook, для закрытия через 'ctrl C'
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down...");
            stopServer();
            logger.info("Shutting down ok");
            LogManager.shutdown();
        }));

        // Загружаем конфигурацию сервера MFK1500
        DriverProperty driverProperty = DriverProperty.getInstance();
        logger.info("driver properties loaded {}", driverProperty);

        // Парсим файл конфигурации
        try {
            if (!ControllerConfig.parsControllerConfig()) {
                throw new MyServerStartException("controller config loading error");
            }
        } catch (IOException e) {
            throw new MyServerStartException("controller config loading error", e);
        }
        logger.info("controller config loaded");

        // Парсим файлы статистики
        // Выгружаем статистику из файлов .ser
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(DriverProperty.getInstance().getStatisticSerPath(),
                entry -> Files.isRegularFile(entry) && entry.toString().endsWith(".ser"))) {
            stream.forEach(path -> {
                try {
                    statistic.putIfAbsent(path.getFileName().toString()
                                    .replaceFirst("[.][^.]+$", "")
                                    .replaceAll("_", "."),
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
                entry -> !statistic.containsKey(entry.getFileName().toString()))) {
            stream.forEach(path -> getStatistic(path.getFileName().toString()).update());
        } catch (IOException e) {
            logger.warn("error load statistic from pushEvent logs directory", e);
        }

        // Запускаем сервис общения с веб частью сервера
        MessageService.startService();
        logger.info("Message service start");

        // Запускаем сервис проверки запросов на конфигурацию
        ControllerConfig.startUploaderService();
        logger.info("controller config service start");

        // Запускаем службу, которая следить подписан ли сервер на получение запрос на мгновенные данные
        InstantDataService.startService();
        logger.info("Instant data service start");

        // Запускаем службу, которая обрабатывает статистику с определенным интервалом
        runCheckStatisticService();
        logger.info("Statistic service start");

        // Оповещаем, что сервер загружен
        if (serverLoadListener != null) {
            serverLoadListener.onLoad();
        }

        // Запуск netty для прослушивания портов
        bossGroup = new NioEventLoopGroup(1);

        if (driverProperty.getnWorkThreads() == 0) {
            workerGroup = new NioEventLoopGroup();
            logger.info("Starting netty server on {} processors", SystemPropertyUtil.getInt("io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));
        } else {
            workerGroup = new NioEventLoopGroup(driverProperty.getnWorkThreads());
            logger.info("Starting netty server on {} processors", driverProperty.getnWorkThreads());
        }

        // TODO Возможно надо отделить обработку сообщений от pushEvent
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            int localPort = ch.localAddress().getPort();
                            if (localPort == DriverProperty.getInstance().getMessageServicePort()) {
                                ch.pipeline().addLast(new ObjectDecoder(ClassResolvers.cacheDisabled(null)));
                                ch.pipeline().addLast(new CommandHandler());
                            }

                            if (localPort == DriverProperty.getInstance().getListeningPort()) {
                                String host = ch.remoteAddress().getAddress().getHostAddress();
                                if (!isBlocked(host) && !getStatistic(host).isChanelOpen()) {
                                    logger.info("create new connection from {}", host);
                                    Statistic statistic = getStatistic(host);

                                    ch.pipeline().addLast(new MyChannelTrafficShapingHandler(statistic, 30000));
                                    ch.pipeline().addLast(new ByteArrayEncoder());
                                    ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(65537, 0, 2, 0, 0));
                                    ch.pipeline().addLast(new PushEventHandler(statistic));
                                } else {
                                    ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(65537, 0, 2, 0, 0));
                                    ch.pipeline().addLast(new IgnoreHandler(host));
                                }
                            }
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128);

            channelFutureList.add(bootstrap.bind(DriverProperty.getInstance().getMessageServicePort()).sync());
            channelFutureList.add(bootstrap.bind(DriverProperty.getInstance().getListeningPort()).sync());

            logger.info("Listening netty server start on ports {} {}",
                    DriverProperty.getInstance().getMessageServicePort(),
                    DriverProperty.getInstance().getListeningPort());
        } catch (InterruptedException e) {
            logger.warn("Netty server is interrupted", e);
            throw new MyServerStartException("Netty start error", e);
        } catch (Exception e) {
            logger.warn("Netty server problem", e);
            throw new MyServerStartException("Netty start error", e);
        }
    }

    private static boolean isBlocked(String ip) {
        return isBlocked(ip, null);
    }

    public static boolean isBlocked(String ip, BlockType blockType) {
        Statistic st = statistic.get(ip);
        return ((st != null) && st.isBlock(blockType));
    }

    /**
     * Метод останавливает работу приложения
     */
    public static void stopServer() {
        ControllerConfig.stopUploaderService();
        InstantDataService.stopService();
        MessageService.stopService();

        WORKER_SERVICE.shutdown();

        // Закрывает netty сервер
        for (ChannelFuture channelFuture: channelFutureList) {
            try {
                channelFuture.channel().close().await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.warn("Error close netty", e);
            }
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }

        // Сериализуем статистику в файлы
        statistic.forEach((k, v) -> {
            logger.info("Serialize {}", k);
            v.close();
            serialize(v);
        });
        statistic.clear();

        logger.info("MFK1500 server stop");
    }

    /**
     * Получение {@link Statistic} по переданному ключу,
     * если объект отсутствует, то создается новый с стандартными параметрами
     * @param ip ключ для статистики
     * @return значение оссоциированное с переданным ключом.
     */
    public static Statistic getStatistic(String ip) {
        statistic.putIfAbsent(ip, new Statistic(ip, clientChangeListener));
        return statistic.get(ip);
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

        if (clientChangeListener != null) {
            clientChangeListener.update();
        }
    }

    /**
     * Сериализация объекта {@link Statistic} в файл
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

            if (clientChangeListener != null) {
                statistic.setEvent(clientChangeListener);
                clientChangeListener.addItem(statistic);
            }

            statistic.update();

            return Optional.of(statistic);
        } catch (IOException | ClassNotFoundException e) {
            logger.warn("deserialize error", e);
        }
        return Optional.empty();
    }

    /**
     * Метод запускает службы обработки статистики раз в 20 минут
     */
    private static void runCheckStatisticService() {
        // Определяем сколько минут осталось до ближайщего 20 минутного интервала (00/20/40 минут)
        long untilNextTime = 21;
        for (int i = 0; untilNextTime >= 20; i++) {
            untilNextTime = LocalDateTime.now().until(
                    LocalDateTime.now()
                            .plusHours(1)
                            .withMinute(0)
                            .minusMinutes(i * 20), ChronoUnit.MINUTES);
        }
        // Определяем сколько минут осталось до полуночи
        long midnight = LocalDateTime.now().until(LocalDate.now().plusDays(1).atStartOfDay(), ChronoUnit.MINUTES) + 5;
        // Определяем сколько минут осталось до ближайшего часа
        long nextHour = LocalDateTime.now().until(LocalDateTime.now().plusHours(1).withMinute(0), ChronoUnit.MINUTES) + 1;

        // Автоматическое снятие блокировок Ошибка сервера и Разрыв соединения.
        // Срабатывает раз в 20 минут.
        WORKER_SERVICE.scheduleAtFixedRate(() -> {
            logger.info("Removing of blocks triggers every 20 minutes");
            statistic.forEach((k, st) -> st.unblock(BlockType.SERVER_ERROR, BlockType.LINK_ERROR));
        }, untilNextTime, 20, TimeUnit.MINUTES);

        // Резервирование логов статистики.
        // Срабатывает раз в час.
        WORKER_SERVICE.scheduleAtFixedRate(() -> {
            logger.info("Reserving of statistic logs triggers every hour");
            statistic.forEach((k, st) -> {
                serialize(st);
                if (st.isSocketHung()) {
                    logger.info("close hung socket for {}", k);
                    st.close();
                    st.update();
                }
            });
        }, nextHour, TimeUnit.HOURS.toMinutes(1), TimeUnit.MINUTES);

        // Ежедневное обновление статистики и файлов логов.
        // Срабатывает в начале каждого дня.
        WORKER_SERVICE.scheduleAtFixedRate(() -> {
            logger.info("Updating of statistics triggers every day");
            statistic.forEach((k, st) -> {
                st.close();
                st.clearSocketCount();
                st.clearLastDayDataGroups();
                st.clearDayTraffic();
                st.clearMarkV2();
                st.clearObjectModel();
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
            });
        }, midnight, TimeUnit.DAYS.toMinutes(1), TimeUnit.MINUTES);
    }

    public static void setOnLoadListener(ServiceLoadListener serverLoadListener) {
        MFK1500Server.serverLoadListener = serverLoadListener;
    }

    public static void setClientChangeListener(Event clientChangeListener) {
        MFK1500Server.clientChangeListener = clientChangeListener;
    }

    private static class MyDefaultThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        MyDefaultThreadFactory(String prefix) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
            namePrefix = prefix +
                    "-pool-" +
                    poolNumber.getAndIncrement() +
                    "-thread-";
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}
