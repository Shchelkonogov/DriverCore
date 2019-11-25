package ru.tecon.server;

import ru.tecon.beanInterface.LoadOPCRemote;

import javax.naming.NamingException;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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

    private static Map<String, Map<String, List<String>>> config = new HashMap<>();
    private static List<String> configList = new ArrayList<>();
    private static String serverName;
    private static String logFolder;

    public static void main(String[] args) {
        Properties prop = new Properties();
        try {
            prop.load(new FileInputStream(args[0]));
        } catch (IOException e) {
            e.printStackTrace();
            LOG.warning("main Error load properties file");
            return;
        }

        serverName = prop.getProperty("serverName");
        logFolder = prop.getProperty("logFolder");

        // Парсим файл конфигурации
        try {
            if (!loadConfig(prop.getProperty("configFile"))) {
                LOG.warning("main Error load config. Parse error");
                return;
            }
        } catch (IOException e) {
            LOG.warning("main Error load config Message: " + e.getMessage());
            return;
        }
        LOG.info("main config load ok");

        // Запускаем службу, которая проверяет запрос от базы на выгрузку конфигурации
        checkConfigService();
        LOG.info("main start config service ok");

        // Запускаем serverSocket
        ServerSocket serverSocket;
        Socket socket;

        try {
            serverSocket = new ServerSocket(Integer.parseInt(prop.getProperty("port")), 100);
        } catch (IOException e) {
            LOG.warning("main Error start server socket Message: " + e.getMessage());
            return;
        }

        LOG.info("main Server socket start ok");

        while (true) {
            try {
                socket = Objects.requireNonNull(serverSocket).accept();
            } catch (IOException e) {
                LOG.warning("main Error create socket connection Message: " + e.getMessage());
                break;
            }

            LOG.info("main New connection from " + socket.getInetAddress().getHostAddress());
            new EchoThread(socket, serverName).start();
        }
    }

    /**
     * Метод парсит файл конфигурации сервера
     * @param filePath путь к файлу
     * @return статус конфигурации
     * @throws IOException если произошла ошибка в разборе файла
     */
    private static boolean loadConfig(String filePath) throws IOException {
        LOG.info("loadConfig Read config file " + filePath);

        BufferedReader reader = Files.newBufferedReader(Paths.get(filePath));

        String line;
        String key1 = null;
        String key2 = null;
        while ((line = reader.readLine()) != null) {
            if (line.trim().startsWith("^")) {
                key1 = line.trim().substring(1);
                if (!config.containsKey(key1)) {
                    config.put(key1, new HashMap<>());
                }
            }
            if (line.trim().startsWith("@") && (line.trim().split(",").length == 2)) {
                key2 = line.trim().split(",")[0].substring(1);
                if ((key1 != null) && !config.get(key1).containsKey(key2)) {
                    config.get(key1).put(key2, new ArrayList<>());
                }
            }
            if (line.trim().startsWith("#")) {
                if ((key1 != null) && (key2 != null)) {
                    if (line.trim().split(":").length == 2) {
                        config.get(key1).get(key2).add(line.trim().split(":")[1] + ":" + line.trim().split(":")[0].substring(1));
                    }
                }
            }
        }

        for (String k1: config.keySet()) {
            for (String k2: config.get(k1).keySet()) {
                configList.addAll(config.get(k1).get(k2));
            }
        }

        return !config.isEmpty();
    }

    /**
     * Сервис который каждые 30 секунд проверяет нету ли
     * запроса на выгрузку конфигурации в базу
     */
    private static void checkConfigService() {
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleWithFixedDelay(() -> {
            try {
                LoadOPCRemote opc = Utils.loadRMI();

                if (opc.isLoadConfig(serverName)) {
                    LOG.info("checkConfigService Request load config");
                    opc.putConfig(configList, serverName);
                    LOG.info("checkConfigService Config load ok");
                }
            } catch (NamingException e) {
                LOG.warning("checkConfigService Error with service Message: " + e.getMessage());
            }
        }, 5, 30, TimeUnit.SECONDS);
    }

    static List<String> getConfigNames(String bufferNumber, String eventCode) {
        return config.containsKey(bufferNumber) ? config.get(bufferNumber).get(eventCode) : null;
    }

    static String getLogFolder() {
        return logFolder;
    }
}
