package ru.tecon.instantData;

/**
 * Перечисление простых типов для которых реализован алгоритм чтения данных
 */
public enum InstantDataTypes {

//    STRING(1),
    BOOL(1),
    REAL(4),
    DINT(4),
    INT(2);

    private int size;

    InstantDataTypes(int size) {
        this.size = size;
    }

    public int getSize() {
        return size;
    }

    public static boolean isContains(String value) {
        try {
            InstantDataTypes.valueOf(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
