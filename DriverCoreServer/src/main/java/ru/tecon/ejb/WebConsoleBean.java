package ru.tecon.ejb;

import ru.tecon.model.Command;

import javax.annotation.Resource;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.*;
import javax.sql.DataSource;
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

    @Resource(name = "jms/ConnectionFactory")
    private ConnectionFactory connectionFactory;

    @Resource(name = "jms/Topic")
    private Destination destination;

    /**
     * Метод провоцирет сообщение в jms топик
     * @param command сообщение для jms
     */
    public void produceMessage(Command command) {
        try (Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            MessageProducer messageProducer = session.createProducer(destination);

            messageProducer.send(session.createObjectMessage(command));
        } catch (JMSException e) {
            LOGGER.warning("error produce message " + command + " " + e.getMessage());
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
