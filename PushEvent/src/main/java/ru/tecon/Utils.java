package ru.tecon;

import ru.tecon.beanInterface.LoadOPCRemote;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.*;

public class Utils {

    public static LoadOPCRemote loadRMI() throws NamingException {
        Hashtable<String, String> ht = new Hashtable<>();
        ht.put(Context.INITIAL_CONTEXT_FACTORY, "weblogic.jndi.WLInitialContextFactory");
        ht.put(Context.PROVIDER_URL, "t3://" + ProjectProperty.getServerURI() + ":" + ProjectProperty.getServerPort());

        Context ctx = new InitialContext(ht);

        return  (LoadOPCRemote) ctx.lookup("ejb.LoadOPC#ru.tecon.beanInterface.LoadOPCRemote");
    }
}
