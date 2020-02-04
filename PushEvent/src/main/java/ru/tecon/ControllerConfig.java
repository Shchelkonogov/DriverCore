package ru.tecon;

import com.jcraft.jsch.*;
import ru.tecon.beanInterface.LoadOPCRemote;
import ru.tecon.instantData.InstantDataTypes;
import ru.tecon.server.Utils;
import ru.tecon.webSocket.WebSocketClient;

import javax.naming.NamingException;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Класс для работы с конфигурацией контроллера
 */
public class ControllerConfig {

    private static final Logger LOG = Logger.getLogger(ControllerConfig.class.getName());

    private static Map<String, Map<String, List<String>>> config = new HashMap<>();
    private static List<String> configList = new ArrayList<>();

    /**
     * Метод парсит текстовый файл конфигурации контроллера
     * @return true если все впорядке
     * @throws IOException если ошибка в чтении файла
     */
    public static boolean parsControllerConfig() throws IOException {
        LOG.info("read config file " + ProjectProperty.getConfigFile());

        BufferedReader reader = Files.newBufferedReader(Paths.get(ProjectProperty.getConfigFile()));

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
                    if (line.trim().split(",").length == 7) {
                        config.get(key1).get(key2).add(line.trim().split(",")[6] + ":" +
                                line.trim().split(",")[0].substring(1) + ":" +
                                line.trim().split(",")[3]);
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
     * Метод запускает службу которая обрабатывает запросы на конфигурацию из базы
     */
    public static void startUploaderService() {
        if (ProjectProperty.isPushFromDataBase()) {
            new WebSocketClient().connectToWebSocketServer();
        } else {
            Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(() -> {
                try {
                    LoadOPCRemote opc = Utils.loadRMI();

                    List<String> urlList = opc.getURLToLoadConfig(ProjectProperty.getServerName());

                    if (!urlList.isEmpty()) {
                        Map<String, List<String>> urlConfig = new HashMap<>();
                        urlList.forEach(s -> urlConfig.put(s, getInstantConfigFromURL(s)));

                        opc.putConfig(configList, urlConfig, ProjectProperty.getServerName());
                    }
                } catch (NamingException e) {
                    LOG.warning("error with upload config service. Message: " + e.getMessage());
                }
            }, 5, 30, TimeUnit.SECONDS);
        }
    }

    /**
     * Метод отправляет конфигурацию в базу
     */
    public static void uploadConfig(String url) {
        try {
            Map<String, List<String>> urlConfig = new HashMap<>();
            urlConfig.put(url, getInstantConfigFromURL(url));

            Utils.loadRMI().putConfig(configList, urlConfig, ProjectProperty.getServerName());
        } catch (NamingException e) {
            LOG.info("error load RMI: " + e.getMessage());
        }
    }

    public static List<String> getConfigNames(String bufferNumber, String eventCode) {
        return config.containsKey(bufferNumber) ? config.get(bufferNumber).get(eventCode) : null;
    }

    /**
     * Метод по ssh общается с MFK1500 и получает от него конфигурацию
     * @param url url прибора
     * @return список параметров конфигурации
     */
    private static List<String> getInstantConfigFromURL(String url) {
        List<String> result = new ArrayList<>();

        try {
            Properties prop = new Properties();
            prop.put("StrictHostKeyChecking", "no");

            JSch jsch = new JSch();
            Session session = jsch.getSession("root", url, 22);
            session.setPassword("tecon");
            session.setConfig(prop);
            session.connect();

            Channel channel = session.openChannel("exec");
            ((ChannelExec)channel).setCommand("cat /user/IDS*");
            channel.connect();

            BufferedInputStream in = new BufferedInputStream(channel.getInputStream());

            int readByte;
            StringBuilder sb = new StringBuilder();

            while((readByte = in.read()) != -1) {
                sb.append((char)readByte);
            }

            String types = sb.substring(sb.indexOf("[TYPE]") + "[TYPE]".length(), sb.indexOf("[DEVTYP]"));
            Map<Integer, String> tMap = Arrays.stream(types.split("\n"))
                    .filter(s -> {
                        if (s.startsWith("T")) {
                            for (InstantDataTypes type: InstantDataTypes.values()) {
                                if (s.substring(s.indexOf('=') + 1, s.indexOf(',')).equals(type.name())) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    })
                    .map(s -> s.substring(1).split(",")[0])
                    .collect(Collectors.toMap(k -> Integer.parseInt(k.substring(0, k.indexOf("="))), v -> v.substring(v.indexOf("=") + 1)));

            String variables = sb.substring(sb.indexOf("[VARIABLE]") + "[VARIABLE]".length(), sb.indexOf("[END]"));
            result = Arrays.stream(variables.split("\n"))
                    .filter(s -> s.startsWith("V") && tMap.keySet().contains(Integer.parseInt(s.split(",")[6])))
                    .map(s -> s.substring(s.indexOf("=") + 1).split(",")[0] + ":" + tMap.get(Integer.parseInt(s.split(",")[6])) + ":Текущие данные")
                    .collect(Collectors.toList());

            channel.disconnect();
            session.disconnect();
        } catch (JSchException e) {
            LOG.warning("error ssh connect: " + e.getMessage());
        } catch (IOException e) {
            LOG.warning("error ssh file read: " + e.getMessage());
        }

        return result;
    }
}
