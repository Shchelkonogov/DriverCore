package ru.tecon.server;

/**
 * Интерфейс для слушателя загрузки драйвера mfk-1500
 * @author Maksim Shchelkonogov
 */
public interface ServiceLoadListener {

    /**
     * Событие загрузки драйвера
     */
    void onLoad();
}
