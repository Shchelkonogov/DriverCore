package ru.tecon;

import ru.tecon.beanInterface.LoadOPCRemote;
import ru.tecon.dataUploaderClient.beanInterface.DataUploaderAppBeanRemote;
import ru.tecon.dataUploaderClient.beanInterface.instantData.InstantDataSingletonRemote;
import ru.tecon.mfk1500Server.DriverProperty;
import weblogic.jndi.WLInitialContextFactory;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Hashtable;

/**
 * Класс утилита
 */
public class Utils {

    private static Context getContext() throws NamingException {
        Hashtable<String, String> ht = new Hashtable<>();
        ht.put(Context.INITIAL_CONTEXT_FACTORY, WLInitialContextFactory.class.getName());
        ht.put(Context.PROVIDER_URL, "t3://" + DriverProperty.getInstance().getServerURI() + ":" + DriverProperty.getInstance().getServerPort());

        return new InitialContext(ht);
    }

//    Используется для песочницы на payara
//    private static Context getContext() throws NamingException {
//        Hashtable<String, String> ht = new Hashtable<>();
//        ht.put(Context.INITIAL_CONTEXT_FACTORY, RemoteEJBContextFactory.FACTORY_CLASS);
//        ht.put(Context.PROVIDER_URL, "http://" + DriverProperty.getInstance().getServerURI() + ":" + DriverProperty.getInstance().getServerPort() + "/ejb-invoker");
//
//        return new InitialContext(ht);
//    }

    /**
     * Метод возвращает ejb bean класс для работы с базой
     * @return ejb класс
     * @throws NamingException ошибка
     */
    public static LoadOPCRemote loadRMI() throws NamingException {
        return  (LoadOPCRemote) getContext().lookup("ejb/LoadOPC#" + LoadOPCRemote.class.getName());
        // Для payara
//        return  (LoadOPCRemote) getContext().lookup("java:global/DriverCore/LoadOPC!" + LoadOPCRemote.class.getName());
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
