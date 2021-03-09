package ru.tecon.isacom;

/**
 * Перечисление простых типов для которых реализован алгоритм чтения данных
 */
public enum IsacomSimpleTypes {

    BOOL(1),
    REAL(4),
    DINT(4),
    INT(2),
    TIME(4);

    private int size;

    IsacomSimpleTypes(int size) {
        this.size = size;
    }

    public int getSize() {
        return size;
    }

    public static boolean isContains(String value) {
        try {
            IsacomSimpleTypes.valueOf(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
