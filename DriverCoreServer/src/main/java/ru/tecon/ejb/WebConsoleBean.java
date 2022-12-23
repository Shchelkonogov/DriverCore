package ru.tecon.ejb;

import io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import ru.tecon.mfk1500Server.web.ejb.DriverAppSB;
import ru.tecon.model.Command;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.sql.DataSource;
import java.io.IOException;
import java.net.Socket;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stateless bean для работы с console от драйвера
 */
@Stateless
@LocalBean
public class WebConsoleBean {

    private static final Logger LOGGER = Logger.getLogger(WebConsoleBean.class.getName());

    private static final String PRO_RESIGN_OBJECT = "{call change_mfk_conf(?)}";

    @Resource(name = "jdbc/DataSource")
    private DataSource ds;

    @EJB
    private DriverAppSB driverAppSB;

    /**
     * Метод провоцирет сообщение в jms топик
     * @param command сообщение для jms
     */
    public void produceMessage(Command command) {
        try {
            String server = command.getParameter("server");

            if (server == null) {
                for (String value: driverAppSB.values()) {
                    sendMessage(command, value.split(":")[0], Integer.parseInt(value.split(":")[1]));
                }
            } else {
                String serverUrl = driverAppSB.get(server);
                if (serverUrl != null) {
                    sendMessage(command, serverUrl.split(":")[0], Integer.parseInt(serverUrl.split(":")[1]));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "error send command", e);
        }
    }

    private void sendMessage(Command command, String host, int port) {
        LOGGER.log(Level.INFO, "Send command {0} to {1}:{2}", new Object[]{command, host, port});

        try (Socket socket = new Socket(host, port);
             ObjectEncoderOutputStream out = new ObjectEncoderOutputStream(socket.getOutputStream())) {
            out.writeObject(command);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "error send command", e);
        }
    }

    /**
     * Метод переподписывает оъект в базе
     * @param objName имя объекта
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void ResignObject(String objName) {
        LOGGER.log(Level.INFO, "re-sign object {0}", objName);
        try (java.sql.Connection connect = ds.getConnection();
             CallableStatement cStm = connect.prepareCall(PRO_RESIGN_OBJECT)) {
            cStm.setString(1, objName);
            cStm.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "error re-sign object", e);
        }
    }
}
