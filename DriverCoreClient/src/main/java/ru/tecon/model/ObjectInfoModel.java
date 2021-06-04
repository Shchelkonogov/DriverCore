package ru.tecon.model;

import java.io.Serializable;
import java.util.StringJoiner;

/**
 * Класс описывающий системные настройки MFK1500
 */
public class ObjectInfoModel implements Serializable {

    private String name;
    private String value;
    private boolean write = false;

    public ObjectInfoModel() {
    }

    public ObjectInfoModel(String name, String value, boolean write) {
        this.name = name;
        this.value = value;
        this.write = write;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public void setWrite(boolean write) {
        this.write = write;
    }

    public boolean isWrite() {
        return write;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ObjectInfoModel.class.getSimpleName() + "[", "]")
                .add("name='" + name + "'")
                .add("value='" + value + "'")
                .add("write=" + write)
                .toString();
    }
}
