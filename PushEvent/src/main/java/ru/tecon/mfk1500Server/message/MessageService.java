package ru.tecon.mfk1500Server.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tecon.mfk1500Server.DriverProperty;
import ru.tecon.mfk1500Server.MFK1500Server;
import ru.tecon.exception.MyServerStartException;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * @author Maksim Shchelkonogov
 */
public class MessageService {

    private static Logger logger = LoggerFactory.getLogger(MessageService.class);

    private static MFK1500Server server;
    private static ScheduledExecutorService service;
    private static ScheduledFuture future;

    public static void startService() {
        server = new MFK1500Server();
        ExecutorService messageService = Executors.newSingleThreadExecutor();
        messageService.submit(() -> server.run());

        subscriptService();

        service = Executors.newSingleThreadScheduledExecutor();
        future = service.scheduleAtFixedRate(() -> {
            try {
                DriverProperty property = DriverProperty.getInstance();

                Response response = ClientBuilder.newClient()
                        .target("http://" + property.getServerURI() + ":" + property.getServerPort() + "/DriverCore/api/checkDriver")
                        .queryParam("name", property.getServerName())
                        .request()
                        .get();

                switch (response.getStatus()) {
                    case 200:
                        break;
                    case 204:
                        subscriptService();
                        break;
                    default:
                        logger.warn("Message service checker subscription unknown response {}", response.getStatus());
                }
            } catch (MyServerStartException ignore) {
            } catch (Exception ex) {
                logger.warn("Message service checker error", ex);
            }
        }, 0, 30, TimeUnit.MINUTES);
    }

    public static void stopService() {
        try {
            DriverProperty property = DriverProperty.getInstance();

            ClientBuilder.newClient()
                    .target("http://" + property.getServerURI() + ":" + property.getServerPort() + "/DriverCore/api/removeDriver")
                    .queryParam("name", property.getServerName())
                    .request()
                    .post(null);

            server.stop();
        } catch (Exception ex) {
            logger.warn("Message service stop error", ex);
            throw new MyServerStartException("Message service stop error", ex);
        }

        if (Objects.nonNull(service)) {
            future.cancel(true);
            service.shutdown();
        }
    }

    private static void subscriptService() {
        DriverProperty property = DriverProperty.getInstance();

        Response response = ClientBuilder.newClient()
                .target("http://" + property.getServerURI() + ":" + property.getServerPort() + "/DriverCore/api/addDriver")
                .queryParam("name", property.getServerName())
                .queryParam("port", property.getMessageServicePort())
                .request()
                .post(null);

        if (response.getStatus() != 201) {
            logger.warn("Error subscript service. response status {}", response.getStatus());
            throw new MyServerStartException("Error subscript service");
        }
    }
}
