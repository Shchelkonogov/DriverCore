package ru.tecon.isacom;

/**
 * Ошибка работы протокола isacom
 */
public class IsacomException extends Exception {

    IsacomException(String message) {
        super(message);
    }
}
