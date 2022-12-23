package ru.tecon.mfk1500Server.web.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.enterprise.inject.Produces;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

/**
 * @author Maksim Shchelkonogov
 */
public class JsonProducer {

    @Produces
    public Gson produceJson() {
        return new Gson();
    }
}

