package ru.tecon.jms;

import ru.tecon.ProjectProperty;
import ru.tecon.Utils;
import ru.tecon.controllerData.ControllerConfig;
import ru.tecon.exception.MyServerStartException;
import ru.tecon.instantData.InstantDataService;
import ru.tecon.model.Command;
import ru.tecon.server.EchoSocketServer;
import ru.tecon.traffic.BlockType;
import ru.tecon.traffic.Statistic;
import weblogic.jndi.WLInitialContextFactory;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Класс для приема и обработки сообщений по JMS
 * @author Maksim Shchelkonogov
 */
public class MessageReceiveService implements MessageListener {

    private static final Logger LOGGER = Logger.getLogger(MessageReceiveService.class.getName());

    private final static String JMS_FACTORY = "jms/ConnectionFactory";
    private final static String TOPIC_NAME = "jms/Topic";

    private TopicConnection connection;
    private TopicSession session;
    private TopicSubscriber subscriber;

    /**
     * Обработчик ошибки jms подключения, пытается переподключиться раз в 30 секунд
     */
    private ExceptionListener listener = e -> {
        LOGGER.log(Level.WARNING, "Connection to the Server has been lost, will retry in 30 seconds", e);

        ExecutorService service = Executors.newSingleThreadExecutor();
        service.submit(() -> {
            try {
                do {
                    stopService();
                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException ignore) {
                    }
                    LOGGER.info("reconnect to jms");
                } while (!initService());
                LOGGER.info("new jms connection created");
            } catch (MyServerStartException ex) {
                LOGGER.warning("error init service");
            }
        });

        service.shutdown();
    };

    private boolean reconnect = false;

    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof ObjectMessage) {
                ObjectMessage objectMessage = (ObjectMessage) message;
                if (objectMessage.getObject() instanceof Command) {
                    Command command = (Command) objectMessage.getObject();

                    LOGGER.info("receive message " + command);

                    if ((command.getParameter("server") != null) &&
                            (command.getParameter("server").equalsIgnoreCase(ProjectProperty.getServerName()))) {
                        switch (command.getName()) {
                            case "loadConfig":
                                ControllerConfig.uploadConfig(command.getParameter("url"));
                                break;
                            case "loadInstantData":
                                InstantDataService.uploadInstantData(command.getParameter("url"));
                                break;
                            case "block":
                                EchoSocketServer.getStatistic().get(command.getParameter("url")).block(BlockType.USER);
                                break;
                            case "unblock":
                                EchoSocketServer.getStatistic().get(command.getParameter("url")).unblockAll();
                                break;
                            case "info":
                                try {
                                    Utils.loadRMI().sendInfo(command.getParameter("sessionID"),
                                            ControllerConfig.getControllerInfo(command.getParameter("url")));
                                } catch (NamingException ex) {
                                    LOGGER.log(Level.WARNING, "error load RMI", ex);
                                }
                                break;
                            case "writeInfo":
                                ControllerConfig.setControllerInfo(command.getParameter("url"),command.getParameter("info"));
                                break;
                            case "blockTraffic":
                                EchoSocketServer.getStatistic().get(command.getParameter("url")).setIgnoreTraffic(false);
                                break;
                            case "unblockTraffic":
                                EchoSocketServer.getStatistic().get(command.getParameter("url")).setIgnoreTraffic(true);
                                break;
                            case "synchronizeDate":
                                ControllerConfig.synchronizeDate(command.getParameter("url"));
                                break;
                            case "remove":
                                EchoSocketServer.removeStatistic(command.getParameter("url"));
                                break;
                            case "getLastConfigNames":
                                try {
                                    Utils.loadRMI().uploadLogData(command.getParameter("sessionID"),
                                            EchoSocketServer.getStatistic().get(command.getParameter("url")).getLastDayDataGroups());
                                } catch (NamingException e) {
                                    LOGGER.log(Level.WARNING, "error send last config names to client", e);
                                }
                                break;
                            case "getConfigGroup":
                                try {
                                    int bufferNumber = Integer.parseInt(command.getParameter("bufferNumber"));
                                    int eventCode = Integer.parseInt(command.getParameter("eventCode"));
                                    int size = Integer.parseInt(command.getParameter("size"));

                                    Utils.loadRMI().uploadConfigNames(command.getParameter("sessionID"),
                                            ControllerConfig.getConfigNames(bufferNumber, eventCode, size));
                                } catch (NumberFormatException e) {
                                    LOGGER.log(Level.WARNING, "error parse group identifier", e);
                                } catch (NamingException e) {
                                    LOGGER.log(Level.WARNING, "error upload config names", e);
                                }
                                break;
                        }
                    } else {
                        if (command.getName().equalsIgnoreCase("requestStatistic")) {
                            try {
                                Utils.loadRMI().uploadStatistic(command.getParameter("sessionID"),
                                        EchoSocketServer.getStatistic().values()
                                                .stream()
                                                .map(Statistic::getWebStatistic)
                                                .collect(Collectors.toList())
                                );
                            } catch (NamingException e) {
                                LOGGER.log(Level.WARNING, "error load RMI", e);
                            }
                        }
                    }
                }
            }
        } catch (JMSException ex) {
            LOGGER.warning("error read message " + ex.getMessage());
        }
    }

    /**
     * Метод инициализирует подключение к jms
     * @return статус подключения
     * @throws MyServerStartException если произошла ошибка
     */
    public boolean initService() throws MyServerStartException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, WLInitialContextFactory.class.getName());
        env.put(Context.PROVIDER_URL, "t3://" + ProjectProperty.getServerURI() + ":" + ProjectProperty.getServerPort());

        try {
            InitialContext ctx = new InitialContext(env);
            TopicConnectionFactory factory = (TopicConnectionFactory) ctx.lookup(JMS_FACTORY);

            connection = factory.createTopicConnection();
            connection.setExceptionListener(listener);

            session = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = (Topic) ctx.lookup(TOPIC_NAME);
            subscriber = session.createSubscriber(topic);
            subscriber.setMessageListener(this);
            connection.start();

            reconnect = true;
            return true;
        } catch (NamingException | JMSException e) {
            if (!reconnect) {
                Utils.error("error init service", e);
            } else {
                LOGGER.warning("error init service");
            }
            return false;
        }
    }

    /**
     * Метод отключается от jms
     */
    public void stopService() {
        try {
            subscriber.close();
            session.close();
            connection.close();
        } catch (JMSException e) {
            LOGGER.warning("error stop service " + e.getMessage());
        }
    }
}
