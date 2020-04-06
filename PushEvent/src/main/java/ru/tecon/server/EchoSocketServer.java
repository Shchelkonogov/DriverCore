package ru.tecon.server;

import ru.tecon.ControllerConfig;
import ru.tecon.ProjectProperty;
import ru.tecon.Utils;
import ru.tecon.exception.MyServerStartException;
import ru.tecon.traffic.Event;
import ru.tecon.traffic.Statistic;
import ru.tecon.instantData.InstantDataService;
import ru.tecon.webSocket.WebSocketClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Сервер для приема socket сообщений от MFK-1500
 * реализация протокола pushEvents
 */
public class EchoSocketServer {

    private static Logger log = Logger.getLogger(EchoSocketServer.class.getName());

    private static final WebSocketClient webSocketClient = new WebSocketClient();

    private static ConcurrentMap<String, Statistic> statistic = new ConcurrentHashMap<>();
    private static ServerSocket serverSocket;

    private static boolean closeApplication = true;

    private static Event event;

    public static void main(String[] args) {
        try {
            startService(args);
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

        // Запускаем webSocketClient
        webSocketClient.connectToWebSocketServer();
        log.info("Web socket client start");
        System.out.println("Сервер получения данных доступен");

        ControllerConfig.startUploaderService();
        log.info("controller config service start");
        System.out.println("Сервис обработки запросов конфигурации контроллера запущен");

        InstantDataService.startService();
        log.info("Instant data service start");
        System.out.println("Сервис обработки запросов на мгновенные данные запущен");

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

            log.info("new connection from " + socket.getInetAddress().getHostAddress());
            EchoThread thread = new EchoThread(socket, ProjectProperty.getServerName());

            thread.start();
        }
    }

    /**
     * Метод останавливает работу приложения
     */
    public static void stopSocket() {
        webSocketClient.stopService();
        ControllerConfig.stopUploaderService();
        InstantDataService.stopService();

        try {
            if (serverSocket != null) {
                serverSocket.close();
                statistic.forEach((k, v) -> v.close());
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
