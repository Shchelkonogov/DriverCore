package ru.tecon;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Класс для получения информации о программной конфигурации
 */
public class ProjectProperty {

    private static Logger LOG = Logger.getLogger(ProjectProperty.class.getName());

    private static int port;
    private static int instantPort;
    private static String configFile;
    private static String instantConfigFile;
    private static String serverName;
    private static String logFolder;
    private static String serverURI;
    private static String serverPort;
    private static boolean pushFromDataBase = false;

    /**
     * Метод выгружает конфигурацию из файла в себя
     * @param path путь к файлу конфигурации
     */
    public static void loadProperties(String path) {
        Properties prop = new Properties();

        try {
            prop.load(new FileInputStream(path));

            port = Integer.parseInt(prop.getProperty("port"));
            instantPort = Integer.parseInt(prop.getProperty("instantPort"));
            configFile = prop.getProperty("configFile");
            instantConfigFile = prop.getProperty("instantFile");
            serverName = prop.getProperty("serverName");
            logFolder = prop.getProperty("logFolder");
            serverURI = prop.getProperty("serverURI");
            serverPort = prop.getProperty("serverPort");

            if ((prop.getProperty("pushFromDataBase") != null) &&
                    (prop.getProperty("pushFromDataBase").equals("true"))) {
                pushFromDataBase = true;
            }

            if ((port == 0) || (configFile == null) || (serverName == null) || (logFolder == null) ||
                    (serverURI == null) || (serverPort == null)) {
                LOG.warning("loadProperties error не хвататет полей в конфигурационном файле");
                System.exit(-1);
            }
        } catch (IOException | NumberFormatException e) {
            LOG.warning("loadProperties error properties load: " + e.getMessage());
            System.exit(-1);
        }
    }

    public static int getPort() {
        return port;
    }

    static String getConfigFile() {
        return configFile;
    }

    public static String getServerName() {
        return serverName;
    }

    public static String getLogFolder() {
        return logFolder;
    }

    public static String getServerURI() {
        return serverURI;
    }

    public static String getServerPort() {
        return serverPort;
    }

    public static boolean isPushFromDataBase() {
        return pushFromDataBase;
    }

    public static int getInstantPort() {
        return instantPort;
    }

    public static String getInstantConfigFile() {
        return instantConfigFile;
    }
}
