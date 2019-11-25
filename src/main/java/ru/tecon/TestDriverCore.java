package ru.tecon;

import ru.tecon.beanInterface.LoadOPCRemote;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Hashtable;

public class TestDriverCore {

    public static void main(String[] args) throws NamingException {
        Hashtable<String, String> ht = new Hashtable<>();
        ht.put(Context.INITIAL_CONTEXT_FACTORY, "weblogic.jndi.WLInitialContextFactory");
        ht.put(Context.PROVIDER_URL, "t3://localhost:7001");

        Context ctx = new InitialContext(ht);

        LoadOPCRemote opc = (LoadOPCRemote) ctx.lookup("ejb.LoadOPC#ru.tecon.beanInterface.LoadOPCRemote");

        if (opc != null) {
            System.out.println("ok");
        } else {
            System.out.println("fail");
        }
    }
}
