package ru.tecon.isacom;

/**
 * Абстрактный класс описания типов
 *
 * @author Maksim Shchelkonogov
 */
public abstract class IsacomType {

    public abstract String getName();

    public abstract int getSize();

    public int getOffset() {
        return 0;
    }
}
