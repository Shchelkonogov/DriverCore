package ru.tecon.log;

import java.util.logging.Level;

public abstract class LogEvent {

    public abstract void log(Level level, String message);

    public void info(String message) {
        log(Level.INFO, message);
    }

    public void warning(String message) {
        log(Level.WARNING, message);
    }
}
