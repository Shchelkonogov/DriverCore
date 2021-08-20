package ru.tecon;

import ru.tecon.beanInterface.LoadOPCRemote;
import ru.tecon.dataUploaderClient.beanInterface.DataUploaderAppBeanRemote;
import ru.tecon.dataUploaderClient.beanInterface.instantData.InstantDataSingletonRemote;
import ru.tecon.exception.MyServerStartException;
import ru.tecon.server.EchoSocketServer;
import weblogic.jndi.WLInitialContextFactory;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Класс утилита
 */
public class Utils {

    private static Logger log = Logger.getLogger(Utils.class.getName());

    private static Context getContext() throws NamingException {
        Hashtable<String, String> ht = new Hashtable<>();
        ht.put(Context.INITIAL_CONTEXT_FACTORY, WLInitialContextFactory.class.getName());
        ht.put(Context.PROVIDER_URL, "t3://" + ProjectProperty.getServerURI() + ":" + ProjectProperty.getServerPort());

        return new InitialContext(ht);
    }

    /**
     * Метод возвращает ejb bean класс для работы с базой
     * @return ejb класс
     * @throws NamingException ошибка
     */
    public static LoadOPCRemote loadRMI() throws NamingException {
        return  (LoadOPCRemote) getContext().lookup("ejb/LoadOPC#" + LoadOPCRemote.class.getName());
    }

    /**
     * Метод возвращает ejb bean класс для работы с мгновенными данными
     * @return ejb класс
     * @throws NamingException ошибка загрузки удаленного класса
     */
    public static InstantDataSingletonRemote getInstantEJB() throws NamingException {
        return (InstantDataSingletonRemote) getContext().lookup("ejb/InstantDataSingleton#" + InstantDataSingletonRemote.class.getName());
    }

    /**
     * Метод возвращает ejb bean класс для работы с загрузкой данных
     * @return ejb класс
     * @throws NamingException ошибка загрузки удаленного класса
     */
    public static DataUploaderAppBeanRemote getDataUploaderAppEJB() throws NamingException {
        return (DataUploaderAppBeanRemote) getContext().lookup("ejb/DataUploaderApp#" + DataUploaderAppBeanRemote.class.getName());
    }

    /**
     * Метод для корректного закрытия приложения в случае ошибки
     * @param message сообщение выключения приложения
     */
    public static void error(String message) throws MyServerStartException {
        error(message, null);
    }

    /**
     * Метод для корректного закрытия приложения в случае ошибки
     * @param message сообщение выключения приложения
     * @param ex java exception для отображения
     */
    public static void error(String message, Exception ex) throws MyServerStartException {
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
            throw new MyServerStartException(message);
        }
    }

    public static String humanReadableByteCountBin(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.1f %ciB", value / 1024.0, ci.current());
    }

    public static String humanReadableByteCountSI(long bytes) {
        if (-1000 < bytes && bytes < 1000) {
            return bytes + " B";
        }
        CharacterIterator ci = new StringCharacterIterator("kMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1000;
            ci.next();
        }
        return String.format("%.1f %cB", bytes / 1000.0, ci.current());
    }
}
