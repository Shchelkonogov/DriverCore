package ru.tecon.mfk1500Server.message;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tecon.exception.MyServerStartException;
import ru.tecon.mfk1500Server.DriverProperty;
import ru.tecon.mfk1500Server.MFK1500Server;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

/**
 * @author Maksim Shchelkonogov
 */
public class MessageService {

    private static Logger logger = LoggerFactory.getLogger(MessageService.class);

    public static void startService() {
        subscriptService();

        MFK1500Server.WORKER_SERVICE.scheduleAtFixedRate(() -> {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                DriverProperty property = DriverProperty.getInstance();

                URI build = new URIBuilder()
                        .setScheme("http")
                        .setHost(property.getServerURI())
                        .setPort(Integer.parseInt(property.getServerPort()))
                        .setPathSegments("DriverCore", "api", "checkDriver")
                        .addParameter("name", property.getServerName())
                        .build();

                HttpGet httpGet = new HttpGet(build);

                Integer status = httpClient.execute(httpGet, response -> response.getStatusLine().getStatusCode());

                switch (status) {
                    case 200:
                        break;
                    case 204:
                        subscriptService();
                        break;
                    default:
                        logger.warn("Message service checker subscription unknown response {}", status);
                }
            } catch (MyServerStartException ignore) {
            } catch (Exception ex) {
                logger.warn("Message service checker error", ex);
            }
        }, 0, 30, TimeUnit.MINUTES);
    }

    public static void stopService() {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            DriverProperty property = DriverProperty.getInstance();

            URI build = new URIBuilder()
                    .setScheme("http")
                    .setHost(property.getServerURI())
                    .setPort(Integer.parseInt(property.getServerPort()))
                    .setPathSegments("DriverCore", "api", "removeDriver")
                    .addParameter("name", property.getServerName())
                    .build();

            HttpPost httpPost = new HttpPost(build);

            Integer status = httpClient.execute(httpPost, response -> response.getStatusLine().getStatusCode());

            logger.info("remove driver status {}", status);
        } catch (Exception ex) {
            logger.warn("Message service stop error", ex);
            throw new MyServerStartException("Message service stop error", ex);
        }
    }

    public static void subscriptService() {
        DriverProperty property = DriverProperty.getInstance();

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            URI build = new URIBuilder()
                    .setScheme("http")
                    .setHost(property.getServerURI())
                    .setPort(Integer.parseInt(property.getServerPort()))
                    .setPathSegments("DriverCore", "api", "addDriver")
                    .addParameter("name", property.getServerName())
                    .addParameter("port", String.valueOf(property.getMessageServicePort()))
                    .build();

            HttpPost httpPost = new HttpPost(build);

            Integer status = httpClient.execute(httpPost, response -> response.getStatusLine().getStatusCode());
            if (status != 201) {
                logger.warn("Error subscript service. response status {}", status);
                throw new MyServerStartException("Error subscript service");
            }
        } catch (IOException | URISyntaxException e) {
            throw new MyServerStartException("Error subscript service", e);
        }
    }
}
