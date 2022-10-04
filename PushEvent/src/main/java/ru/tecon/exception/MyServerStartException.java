package ru.tecon.exception;

public class MyServerStartException extends RuntimeException {

    public MyServerStartException(String message) {
        super(message);
    }

    public MyServerStartException(String message, Throwable cause) {
        super(message, cause);
    }
}
