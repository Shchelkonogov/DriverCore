package ru.tecon.mfk1500Server.web.ejb;

import com.google.gson.Gson;
import io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import ru.tecon.model.Command;

import javax.annotation.PreDestroy;
import javax.ejb.LocalBean;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import java.io.IOException;
import java.net.Socket;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Maksim Shchelkonogov
 */
@Singleton
@LocalBean
@Startup
public class DriverAppSB {

    @Inject
    private Logger logger;

    @Inject
    private Gson json;

    private Map<String, String> drivers = new HashMap<>();

    public String put(String key, String value) {
        return drivers.put(key, value);
    }

    public String remove(String key) {
        return drivers.remove(key);
    }

    public boolean containsKey(String key) {
        return drivers.containsKey(key);
    }

    public String toJSON() {
        return json.toJson(drivers);
    }

    public Collection<String> values() {
        return drivers.values();
    }

    public String get(String key) {
        return drivers.get(key);
    }

    /**
     * При перезапуске отправляет команду подключенным серверам MFk1500 на переподпиание
     */
    @PreDestroy
    private void destroy() {
        for (String value: drivers.values()) {
            Command command = new Command("reSubDriver");
            String host = value.split(":")[0];
            int port = Integer.parseInt(value.split(":")[1]);

            logger.log(Level.INFO, "Send command {0} to {1}:{2}", new Object[]{command, host, port});

            try (Socket socket = new Socket(host, port);
                 ObjectEncoderOutputStream out = new ObjectEncoderOutputStream(socket.getOutputStream())) {
                out.writeObject(command);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
