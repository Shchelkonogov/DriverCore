package ru.tecon;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@ServerEndpoint("/ws/{serverName}")
public class WebSocketServer {

    private static final Logger LOG = Logger.getLogger(WebSocketServer.class.getName());
    private static final Map<Session, String> SESSIONS = new ConcurrentHashMap<>();
    private static final Set<Session> CLIENT_SESSIONS = ConcurrentHashMap.newKeySet();

    @OnOpen
    public void onOpen(@PathParam("serverName") String serverName, Session session) {
        if (serverName.equals("client")) {
            CLIENT_SESSIONS.add(session);
        } else {
            SESSIONS.put(session, serverName);
        }
    }

    @OnClose
    public void onClose(Session session) {
        LOG.info("close server session: " + SESSIONS.get(session) + " or client session: " + session.getId());
        SESSIONS.remove(session);
        CLIENT_SESSIONS.remove(session);
    }

    @OnMessage
    public void onMessage(ByteBuffer message, Session session) {
        if (!message.equals(ByteBuffer.wrap("ping message server".getBytes()))) {
            try {
                session.close();
            } catch (IOException e) {
                LOG.warning("onMessage error: " + e.getMessage());
            }
        }
    }

    @OnMessage
    public void onMessage(String message) {
        if (!message.equals("ping message server")) {
            LOG.info("onMessage: " + message);
        }
    }

    /**
     * Метод отсылает собщение по определенному адресу, если с ним есть связь
     * @param to адрес
     * @param message сообщение
     */
    public static void sendTo(String to, String message) {
        LOG.info("sendTo: " + to + " message: " + message);
        synchronized (SESSIONS) {
            for (Map.Entry<Session, String> entry: SESSIONS.entrySet()) {
                if (entry.getValue().equals(to) && entry.getKey().isOpen()) {
                    entry.getKey().getAsyncRemote().sendText(message);
                }
            }
        }
    }

    public static void sendAll(String message) {
        LOG.info("sendTo message: " + message);
        synchronized (SESSIONS) {
            for (Map.Entry<Session, String> entry: SESSIONS.entrySet()) {
                if (entry.getKey().isOpen()) {
                    entry.getKey().getAsyncRemote().sendText(message);
                }
            }
        }
    }

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
}
