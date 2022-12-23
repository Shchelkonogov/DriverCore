package ru.tecon.mfk1500Server;

import ru.tecon.exception.MyServerStartException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author Maksim Shchelkonogov
 */
public class DriverProperty {

    private static volatile DriverProperty instance;
    private static final Object mutex = new Object();

    private static final List<String> REQUIRED_PARAMETERS = new ArrayList<>(
            Arrays.asList("listeningPort", "historyConfigFile", "instantConfigFile", "serverName", "serverURI",
                    "serverPort", "instantPort", "trafficLimit", "resourceNumber"));

    private int listeningPort;
    private int instantPort;
    private int sshPort;
    private int trafficLimit;
    private int resourceNumber;
    private int nWorkThreads;

    private Path instantConfigPath;
    private Path historyConfigPath;
    private Path pushEventLogPath;
    private Path statisticSerPath;

    private String serverName;
    private String serverPort;
    private String serverURI;

    private final String sshLogin = "root";
    private final String sshPassword = "tecon";
    private final String PushEventLastConfig = "lastConfigNames.txt";
    private final int messageServicePort = 20200;

    // TODO Надо избавиться от этого
    private final boolean checkRequestService = true;

    public static DriverProperty getInstance() {
        DriverProperty result = instance;
        if (result == null) {
            synchronized (mutex) {
                result = instance;
                if (result == null) {
                    instance = result = new DriverProperty();
                }
            }
        }
        return result;
    }

    private DriverProperty() {
        String userDir = System.getProperty("userDir", System.getProperty("user.dir"));

        Properties prop = new Properties();

        try (InputStream in = Files.newInputStream(Paths.get(System.getProperty("driverConfig")))) {
            prop.load(in);
        } catch (IOException ex) {
            throw new MyServerStartException("error parse config file", ex);
        } catch (NullPointerException ex) {
            throw new MyServerStartException("jvm option -D driverConfig is required", ex);
        }

        for (String parameterName: REQUIRED_PARAMETERS) {
            if (!prop.containsKey(parameterName)) {
                throw new MyServerStartException("parameter " + parameterName + " is required in driverConfig.properties file");
            }
        }

        try {
            listeningPort = Integer.parseInt(prop.getProperty("listeningPort"));
            instantPort = Integer.parseInt(prop.getProperty("instantPort"));
            sshPort = Integer.parseInt(prop.getProperty("sshPort", "22"));
            trafficLimit = Integer.parseInt(prop.getProperty("trafficLimit"));
            resourceNumber = Integer.parseInt(prop.getProperty("resourceNumber"));
            nWorkThreads = Integer.parseInt(prop.getProperty("nWorkThreads", "0"));
        } catch (NumberFormatException ex) {
            throw new MyServerStartException("error parse port numbers", ex);
        }

        String property;

        property = prop.getProperty("historyConfigFile");
        historyConfigPath = Paths.get(property.startsWith("/") ? userDir + property : property);

        property = prop.getProperty("instantConfigFile");
        instantConfigPath = Paths.get(property.startsWith("/") ? userDir + property : property);

        if (!Files.isRegularFile(historyConfigPath) || !Files.isRegularFile(instantConfigPath)) {
            throw new MyServerStartException("config files doesn't exists");
        }

        serverName = prop.getProperty("serverName");
        serverURI = prop.getProperty("serverURI");
        serverPort = prop.getProperty("serverPort");

        pushEventLogPath = Paths.get(userDir + "/logs/pushEvent");
        statisticSerPath = Paths.get(userDir + "/logs/statisticSer");
        checkFolder(pushEventLogPath, statisticSerPath);
    }

    private void checkFolder(Path... paths) {
        try {
            for (Path path: paths) {
                if (!Files.exists(path)) {
                    Files.createDirectories(path);
                }
            }
        } catch (IOException ex) {
            throw new MyServerStartException("error create path", ex);
        }
    }

    public int getListeningPort() {
        return listeningPort;
    }

    public int getInstantPort() {
        return instantPort;
    }

    public int getSshPort() {
        return sshPort;
    }

    public int getTrafficLimit() {
        return trafficLimit;
    }

    public Path getInstantConfigPath() {
        return instantConfigPath;
    }

    public Path getHistoryConfigPath() {
        return historyConfigPath;
    }

    public Path getPushEventLogPath() {
        return pushEventLogPath;
    }

    public Path getStatisticSerPath() {
        return statisticSerPath;
    }

    public String getServerName() {
        return serverName;
    }

    public String getServerPort() {
        return serverPort;
    }

    public String getServerURI() {
        return serverURI;
    }

    public String getSshLogin() {
        return sshLogin;
    }

    public String getSshPassword() {
        return sshPassword;
    }

    public String getPushEventLastConfig() {
        return PushEventLastConfig;
    }

    public int getResourceNumber() {
        return resourceNumber;
    }

    public boolean isCheckRequestService() {
        return checkRequestService;
    }

    public int getMessageServicePort() {
        return messageServicePort;
    }

    public int getnWorkThreads() {
        return nWorkThreads;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", DriverProperty.class.getSimpleName() + "[", "]")
                .add("listeningPort=" + listeningPort)
                .add("instantPort=" + instantPort)
                .add("sshPort=" + sshPort)
                .add("trafficLimit=" + trafficLimit)
                .add("resourceNumber=" + resourceNumber)
                .add("nWorkThreads=" + nWorkThreads)
                .add("instantConfigPath=" + instantConfigPath)
                .add("historyConfigPath=" + historyConfigPath)
                .add("pushEventLogPath=" + pushEventLogPath)
                .add("statisticSerPath=" + statisticSerPath)
                .add("serverName='" + serverName + "'")
                .add("serverPort='" + serverPort + "'")
                .add("serverURI='" + serverURI + "'")
                .add("sshLogin='" + sshLogin + "'")
                .add("sshPassword='" + sshPassword + "'")
                .add("PushEventLastConfig='" + PushEventLastConfig + "'")
                .add("messageServicePort=" + messageServicePort)
                .add("checkRequestService=" + checkRequestService)
                .toString();
    }
}
