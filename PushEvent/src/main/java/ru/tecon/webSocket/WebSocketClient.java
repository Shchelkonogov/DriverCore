package ru.tecon.webSocket;

import ru.tecon.ControllerConfig;
import ru.tecon.ProjectProperty;
import ru.tecon.Utils;
import ru.tecon.exception.MyServerStartException;
import ru.tecon.instantData.InstantDataService;
import ru.tecon.server.EchoSocketServer;
import ru.tecon.traffic.Statistic;

import javax.naming.NamingException;
import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@ClientEndpoint
public class WebSocketClient {

    private Logger log = Logger.getLogger(WebSocketClient.class.getName());

    private boolean restartService;

    private ScheduledExecutorService service;
    private ScheduledFuture future;

    private Session session;

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
    }

    @OnMessage
    public void onMessage(String message) {
        String command;
        String url = "";

        if (message.contains(" ")) {
            command = message.split(" ")[0];
            url = message.split(" ")[1];
        } else {
            command = message;
        }

        switch (command) {
            case "loadConfig":
                ControllerConfig.uploadConfig(url);
                break;
            case "loadInstantData":
                InstantDataService.uploadInstantData(url);
                break;
            case "block":
                EchoSocketServer.getStatistic().get(url).setBlock(true);
                break;
            case "unblock":
                EchoSocketServer.getStatistic().get(url).setBlock(false);
                break;
            case "requestStatistic":
                try {
                    for (Map.Entry<String, Statistic> entry: EchoSocketServer.getStatistic().entrySet()) {
                        Utils.loadRMI().uploadStatistic(entry.getValue().getWebStatistic());
                    }
                } catch (NamingException e) {
                    log.log(Level.WARNING, "error load RMI", e);
                }
                break;
        }
    }

    @OnClose
    public void onClose() {
        if (restartService) {
            try {
                connectToWebSocketServer();
            } catch (MyServerStartException e) {
                System.exit(-1);
            }
        }
    }

    /**
     * Метод запускает работу webSocketClient
     */
    public void connectToWebSocketServer() throws MyServerStartException {
        restartService = true;
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        try {
            URI uri = URI.create("ws://" + ProjectProperty.getServerURI() + ":" +
                    ProjectProperty.getServerPort() + "/DriverCore/ws/" + ProjectProperty.getServerName());
            container.connectToServer(this, uri);

            if ((service == null) || (service.isShutdown())) {
                service = Executors.newSingleThreadScheduledExecutor();
                future = service.scheduleWithFixedDelay(() -> {
                    try {
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("ping message server".getBytes()));
                    } catch (IOException e) {
                        log.log(Level.WARNING, "ConnectToWebSocketServer error", e);
                    }
                }, 28, 28, TimeUnit.SECONDS);
            }
        } catch (DeploymentException | IOException e) {
            Utils.error("Ошибка запуска webSocketClient", e);
        }
    }

    /**
     * Метод останавливает работу websocketClient;
     */
    public void stopService() {
        if (service != null) {
            future.cancel(true);
            service.shutdown();
        }

        restartService = false;

        try {
            if (session != null) {
                session.close();
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "Ошибка закрытия webSocketClient", e);
        }
    }
}
