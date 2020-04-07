package ru.tecon;

import ru.tecon.exception.MyServerStartException;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Класс для получения информации о программной конфигурации
 */
public class ProjectProperty {

    private static Logger LOG = Logger.getLogger(ProjectProperty.class.getName());

    private static final String userDir = System.getProperty("user.dir");

    private static int port;
    private static int instantPort;
    private static Path configFile;
    private static Path instantConfigFile;
    private static String serverName;
    private static Path logFolder;
    private static String serverURI;
    private static String serverPort;
    private static boolean checkRequestService = true;
    private static int trafficLimit;

    /**
     * Метод выгружает конфигурацию из файла в себя
     * @param path путь к файлу конфигурации
     */
    public static void loadProperties(String path) throws MyServerStartException {
        Properties prop = new Properties();

        try {
            if (Files.exists(Paths.get(path))) {
                prop.load(new FileInputStream(path));
            } else {
                prop.load(ProjectProperty.class.getResourceAsStream("/config.properties"));
            }

            port = Integer.parseInt(prop.getProperty("port"));
            instantPort = Integer.parseInt(prop.getProperty("instantPort"));

            String property = prop.getProperty("configFile");
            configFile = Paths.get(property.startsWith("/") ? userDir + property : property);

            property = prop.getProperty("instantFile");
            instantConfigFile = Paths.get(property.startsWith("/") ? userDir + property : property);

            serverName = prop.getProperty("serverName");

            property = prop.getProperty("logFolder");
            logFolder = Paths.get(property.startsWith("/") ? userDir + property : property);

            serverURI = prop.getProperty("serverURI");
            serverPort = prop.getProperty("serverPort");
            trafficLimit = Integer.parseInt(prop.getProperty("trafficLimit"));

            if ((prop.getProperty("checkRequestService") != null) &&
                    (prop.getProperty("checkRequestService").equals("false"))) {
                checkRequestService = false;
            }

            if ((port == 0) || (serverName == null) || (logFolder == null) ||
                    (serverURI == null) || (serverPort == null) || (instantPort == 0) ||
                    !Files.exists(configFile) || !Files.exists(instantConfigFile) || !Files.exists(logFolder)) {
                LOG.warning("loadProperties error не хвататет полей в конфигурационном файле");
                Utils.error("loadProperties error не хвататет полей в конфигурационном файле");
            }
        } catch (IOException | NumberFormatException e) {
            Utils.error("loadProperties error properties load:", e);
        }
    }

    public static int getPort() {
        return port;
    }

    static Path getConfigFile() {
        return configFile;
    }

    public static String getServerName() {
        return serverName;
    }

    public static Path getLogFolder() {
        return logFolder;
    }

    public static String getServerURI() {
        return serverURI;
    }

    public static String getServerPort() {
        return serverPort;
    }

    public static boolean isCheckRequestService() {
        return checkRequestService;
    }

    public static int getInstantPort() {
        return instantPort;
    }

    static Path getInstantConfigFile() {
        return instantConfigFile;
    }

    public static int getTrafficLimit() {
        return trafficLimit;
    }
}
