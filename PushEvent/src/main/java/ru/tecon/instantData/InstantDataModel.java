package ru.tecon.instantData;

import java.util.StringJoiner;

/**
 * Модель данных для отправки информации на контроллер
 */
final class InstantDataModel {

    private String name;
    private String type;
    private int typeSize;
    private int offset;
    private String subType;
    private String fullName;
    private boolean functionType = false;

    InstantDataModel(String name, String type, int typeSize) {
        this.name = name;
        this.type = type;
        this.typeSize = typeSize;
    }

    InstantDataModel(String name, String type, int typeSize, int offset, String subType, String fullName) {
        this(name, type, typeSize);
        this.offset = offset;
        this.subType = subType;
        this.functionType = true;
        this.fullName = fullName;
    }

    String getName() {
        return name;
    }

    String getType() {
        return type;
    }

    int getTypeSize() {
        return typeSize;
    }

    int getOffset() {
        return offset;
    }

    String getSubType() {
        return subType;
    }

    boolean isFunctionType() {
        return functionType;
    }

    String getFullName() {
        return fullName;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", InstantDataModel.class.getSimpleName() + "[", "]")
                .add("name='" + name + "'")
                .add("type='" + type + "'")
                .add("typeSize=" + typeSize)
                .add("offset=" + offset)
                .add("subType='" + subType + "'")
                .add("fullName='" + fullName + "'")
                .add("functionType=" + functionType)
                .toString();
    }
}
