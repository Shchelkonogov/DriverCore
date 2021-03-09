package ru.tecon.isacom;

import java.util.StringJoiner;

/**
 * Модель данных для работы протокола isacom
 */
public final class IsacomModel {

    private String name;
    private IsacomType type;
    private String value;

    private IsacomModel subModel;

    /**
     * Создание нового объекта {@code IsacomModel}
     * @param name имя переменной
     * @param type тип переменной
     */
    public IsacomModel(String name, IsacomType type) {
        this.name = name;
        this.type = type;
    }

    /**
     * Создание нового объекта {@code IsacomModel}
     * @param name имя переменной
     * @param type тип переменной
     * @param value значение переменной
     */
    public IsacomModel(String name, IsacomType type, String value) {
        this.name = name;
        this.type = type;
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public String getName() {
        return name;
    }

    void setValue(String value) {
        this.value = value;
    }

    String getTypeName() {
        return type.getName();
    }

    int getTypeSize() {
        return type.getSize();
    }

    int getOffset() {
        return type.getOffset();
    }

    public IsacomModel getSubModel() {
        return subModel;
    }

    public void setSubModel(IsacomModel subModel) {
        this.subModel = subModel;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", IsacomModel.class.getSimpleName() + "[", "]")
                .add("name='" + name + "'")
                .add("type='" + type + "'")
                .add("value='" + value + "'")
                .toString();
    }
}
