package ru.tecon.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Класс для отправки команд
 */
public class Command implements Serializable {

    private String name;
    private Map<String, String> data = new HashMap<>();

    public Command() {
    }

    public Command(String name) {
        this();
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getData() {
        return data;
    }

    public void setData(Map<String, String> data) {
        this.data = data;
    }

    public String getParameter(String name) {
        return data.get(name);
    }

    public void addParameter(String name, String value) {
        data.put(name, value);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Command.class.getSimpleName() + "[", "]")
                .add("name='" + name + "'")
                .add("data=" + data)
                .toString();
    }
}
