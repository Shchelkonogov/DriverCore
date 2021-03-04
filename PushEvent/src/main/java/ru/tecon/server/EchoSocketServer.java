package ru.tecon.server;

import ru.tecon.ControllerConfig;
import ru.tecon.ProjectProperty;
import ru.tecon.Utils;
import ru.tecon.exception.MyServerStartException;
import ru.tecon.traffic.Event;
import ru.tecon.traffic.Statistic;
import ru.tecon.instantData.InstantDataService;
import ru.tecon.webSocket.WebSocketClient;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Сервер для приема socket сообщений от MFK-1500
 * реализация протокола pushEvents
 */
public class EchoSocketServer {

    private static Logger log = Logger.getLogger(EchoSocketServer.class.getName());

    private static final WebSocketClient WEB_SOCKET_CLIENT = new WebSocketClient();

    private static ConcurrentMap<String, Statistic> statistic = new ConcurrentHashMap<>();

    private static ServerSocket serverSocket;

    private static ScheduledExecutorService service;
    private static ScheduledFuture future;

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
            for (File fileEntry : files) {
                if (!fileEntry.isDirectory()) {
                    String ip = fileEntry.getName().replaceFirst("[.][^.]+$", "").replaceAll("_", ".");
                    Statistic st = new Statistic(ip, event);
                    st.deserialize(fileEntry.toPath().toAbsolutePath().toString());
                    statistic.put(ip, st);
                    try {
                        Files.delete(fileEntry.toPath());
                    } catch (IOException e) {
                        log.log(Level.WARNING, "file delete error", e);
                    }
                }
            }
        }
        log.info("controller config load");
        System.out.println("Конфигурация контроллера загружена");

        // Запускаем WEB_SOCKET_CLIENT
        WEB_SOCKET_CLIENT.connectToWebSocketServer();
        log.info("Web socket client start");
        System.out.println("Сервер получения данных доступен");

        ControllerConfig.startUploaderService();
        log.info("controller config service start");
        System.out.println("Сервис обработки запросов конфигурации контроллера запущен");

        InstantDataService.startService();
        log.info("Instant data service start");
        System.out.println("Сервис обработки запросов на мгновенные данные запущен");

        long midnight= LocalDateTime.now().until(LocalDate.now().plusDays(1).atStartOfDay(), ChronoUnit.MINUTES) + 1;
        service = Executors.newSingleThreadScheduledExecutor();
        future = service.scheduleAtFixedRate(() ->
                statistic.forEach((s, st) -> {
                    st.close();
                    st.clearSocketCount();
                    st.clearDayTraffic();
                    if (LocalDate.now().getDayOfMonth() == 1) {
                        st.clearMonthTraffic();
                    }
                    st.updateObjectName();
                    st.setBlock(false);
                }
        ), midnight, TimeUnit.DAYS.toMinutes(1), TimeUnit.MINUTES);

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
        WEB_SOCKET_CLIENT.stopService();
        ControllerConfig.stopUploaderService();
        InstantDataService.stopService();

        if (Objects.nonNull(service)) {
            future.cancel(true);
            service.shutdown();
        }

        try {
            if (serverSocket != null) {
                serverSocket.close();
                statistic.forEach((k, v) -> {
                    v.close();
                    v.serialize();
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

    public static boolean isBlocked(String ip) {
        Statistic st = statistic.get(ip);
        return ((st != null) && st.isBlock());
    }

    public static ConcurrentMap<String, Statistic> getStatistic() {
        return statistic;
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
}
