package ru.tecon.webSocket;

import ru.tecon.ControllerConfig;
import ru.tecon.ProjectProperty;
import ru.tecon.instantData.InstantDataService;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@ClientEndpoint
public class WebSocketClient {

    private Logger LOG = Logger.getLogger(WebSocketClient.class.getName());

    private static boolean serviceStart = false;

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
        serviceStart = false;
        connectToWebSocketServer();
    }

    public synchronized void connectToWebSocketServer() {
        if (!serviceStart) {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            try {
                URI uri = URI.create("ws://" + ProjectProperty.getServerURI() + ":" +
                        ProjectProperty.getServerPort() + "/DriverCore/ws/" + ProjectProperty.getServerName());
                container.connectToServer(this, uri);

                ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
                service.scheduleWithFixedDelay(() -> {
                    try {
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("ping message server".getBytes()));
                    } catch (IOException e) {
                        LOG.warning("connectToWebSocketServer error: " + e.getMessage());
                    }
                }, 28, 28, TimeUnit.SECONDS);
            } catch (DeploymentException | IOException e) {
                System.exit(-1);
            }
            serviceStart = true;
        }
    }
}
