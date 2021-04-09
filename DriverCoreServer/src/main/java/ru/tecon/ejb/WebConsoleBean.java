package ru.tecon.ejb;

import ru.tecon.model.Command;

import javax.annotation.Resource;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.jms.*;
import java.util.logging.Logger;

/**
 * Stateless bean для работы с console от драйвера
 */
@Stateless
@LocalBean
public class WebConsoleBean {

    private static final Logger LOGGER = Logger.getLogger(WebConsoleBean.class.getName());

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
}
