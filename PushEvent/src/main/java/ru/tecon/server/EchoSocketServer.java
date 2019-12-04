package ru.tecon.server;

import ru.tecon.ControllerConfig;
import ru.tecon.ProjectProperty;
import ru.tecon.instantData.InstantDataService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.logging.Logger;

/**
 * Сервер для приема socket сообщений от MFK-1500
 * реализация протокола pushEvents
 * Обязательный параметры на фходе
 * Путь к файлу с конфигурацией сервера C:\Programs\work\IDEA\WorkSpace\PushEvents\resources\config
 * Номер порта для прослушки socket 20100
 * Имя сервера для идентификации его в базе MFK1500-1
 */
public class EchoSocketServer {

    private static final Logger LOG = Logger.getLogger(EchoSocketServer.class.getName());

    public static void main(String[] args) {
        ProjectProperty.loadProperties(args[0]);
        LOG.info("project properties load");

        // Парсим файл конфигурации
        try {
            if (!ControllerConfig.parsControllerConfig()) {
                LOG.warning("error controller config load. Parse error");
                System.exit(-1);
            }
        } catch (IOException e) {
            LOG.warning("error controller config load message: " + e.getMessage());
            System.exit(-1);
        }
        LOG.info("controller config load");

        ControllerConfig.startUploaderService();
        LOG.info("controller config service start");

        InstantDataService.startService();

        // Запускаем serverSocket
        ServerSocket serverSocket = null;
        Socket socket = null;

        try {
            serverSocket = new ServerSocket(ProjectProperty.getPort(), 100);
        } catch (IOException e) {
            LOG.warning("error start server socket Message: " + e.getMessage());
            System.exit(-1);
        }

        LOG.info("server socket start");

        while (true) {
            try {
                socket = Objects.requireNonNull(serverSocket).accept();
            } catch (IOException e) {
                LOG.warning("error create socket connection Message: " + e.getMessage());
                System.exit(-1);
            }

            LOG.info("new connection from " + socket.getInetAddress().getHostAddress());
            new EchoThread(socket, ProjectProperty.getServerName()).start();
        }
    }
}
