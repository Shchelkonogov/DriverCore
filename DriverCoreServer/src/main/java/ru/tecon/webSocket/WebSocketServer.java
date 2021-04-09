package ru.tecon.webSocket;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Реализация сервера websocket для передачи информации клиентам
 */
@ServerEndpoint("/ws/{serverName}")
public class WebSocketServer {

    private static final Logger LOG = Logger.getLogger(WebSocketServer.class.getName());
    private static final Set<Session> CLIENT_SESSIONS = ConcurrentHashMap.newKeySet();

    @OnOpen
    public void onOpen(@PathParam("serverName") String serverName, Session session) {
        if (serverName.equals("client")) {
            CLIENT_SESSIONS.add(session);
            session.getAsyncRemote().sendText("id:" + session.getId());
        }
    }

    @OnClose
    public void onClose(Session session) {
        LOG.info("close client session: " + session.getId());
        CLIENT_SESSIONS.remove(session);
    }

    @OnMessage
    public void onMessage(String message) {
        if (!message.equals("ping message server")) {
            LOG.info("onMessage: " + message);
        }
    }

    /**
     * Метод отправляет текстовое сообщение всем подлюченным клиентам
     * @param message сообщение
     */
    public static void sendAllClients(String message) {
        LOG.info("sendTo message: " + message);
        synchronized (CLIENT_SESSIONS) {
            for (Session session: CLIENT_SESSIONS) {
                if (session.isOpen()) {
                    session.getAsyncRemote().sendText(message);
                }
            }
        }
    }

    /**
     * Метод отправляет текстовое сообщение клиенту
     * @param sessionID id сессии клиента
     * @param message сообщение
     */
    public static void sendToClient(String sessionID, String message) {
        LOG.info("sendTo message: " + message + " to: " + sessionID);
        synchronized (CLIENT_SESSIONS) {
            for (Session session: CLIENT_SESSIONS) {
                if (session.getId().equals(sessionID) && session.isOpen()) {
                    session.getAsyncRemote().sendText(message);
                }
            }
        }
    }
}
