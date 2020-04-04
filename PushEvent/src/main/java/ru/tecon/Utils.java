package ru.tecon;

import ru.tecon.beanInterface.LoadOPCRemote;
import ru.tecon.server.EchoSocketServer;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Класс утилита
 */
public class Utils {

    private static Logger log = Logger.getLogger(Utils.class.getName());

    /**
     * Метод возвращает ejb bean класс для работы с базой
     * @return ejb класс
     * @throws NamingException ошибка
     */
    public static LoadOPCRemote loadRMI() throws NamingException {
        Hashtable<String, String> ht = new Hashtable<>();
        ht.put(Context.INITIAL_CONTEXT_FACTORY, "weblogic.jndi.WLInitialContextFactory");
        ht.put(Context.PROVIDER_URL, "t3://" + ProjectProperty.getServerURI() + ":" + ProjectProperty.getServerPort());

        Context ctx = new InitialContext(ht);

        return  (LoadOPCRemote) ctx.lookup("ejb.LoadOPC#ru.tecon.beanInterface.LoadOPCRemote");
    }

    /**
     * Метод для корректного закрытия приложения в случае ошибки
     * @param message сообщение выключения приложения
     */
    public static void error(String message) {
        error(message, null);
    }

    /**
     * Метод для корректного закрытия приложения в случае ошибки
     * @param message сообщение выключения приложения
     * @param ex java exception для отображения
     */
    public static void error(String message, Exception ex) {
        if (ex == null) {
            log.warning(message);
            System.out.println(message);
        } else {
            log.log(Level.WARNING, message, ex);
            System.out.println(message + " " + ex.getMessage());
        }
        if (EchoSocketServer.isCloseApplication()) {
            try {
                log.info("Приложение закроется через 5 секунд");
                System.out.println("Приложение закроется через 5 секунд");
                Thread.sleep(5000);
                System.exit(-1);
            } catch (InterruptedException e) {
                log.log(Level.WARNING, "error", e);
            }
        } else {
            EchoSocketServer.stopSocket();
        }
    }
}
