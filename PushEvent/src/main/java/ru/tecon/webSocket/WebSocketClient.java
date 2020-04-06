package ru.tecon.webSocket;

import ru.tecon.ControllerConfig;
import ru.tecon.ProjectProperty;
import ru.tecon.Utils;
import ru.tecon.exception.MyServerStartException;
import ru.tecon.instantData.InstantDataService;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
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
        switch (message.substring(0, message.indexOf(" "))) {
            case "loadConfig":
                ControllerConfig.uploadConfig(message.substring(message.indexOf(" ") + 1));
                break;
            case "loadInstantData":
                InstantDataService.uploadInstantData(message.substring(message.indexOf(" ") + 1));
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
