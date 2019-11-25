package ru.tecon.beanInterface;

import ru.tecon.model.DataModel;

import javax.ejb.Remote;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Remote интерфейс для работы с основными функциями
 * общения драйвера с базой
 */
@Remote
public interface LoadOPCRemote {

    /**
     * Метод проверяет существует ли данный объект в базе
     * и слинкован ли он. Если его нету, то добавляет его в базу.
     * Если он не слинкован, то ничего не происходит
     * @param objectName имя объекта
     * @param serverName имя сервера
     * @return true - если объект есть в базе и он слинкован,
     * false - в противоположном случае
     */
    boolean checkObject(String objectName, String serverName);

    /**
     * Загрузка списка объектов в базу
     * @param objects список объектов
     * @param serverName имя сервера
     */
    void insertOPCObjects(List<String> objects, String serverName);

    /**
     * Метод проверяет запросила ли база конфигурацию сервера
     * @param serverName имя сервера
     * @return true - если база требует конфигурацию,
     * false - в противоположном случае
     */
    boolean isLoadConfig(String serverName);

    /**
     * Метод выгружает конфигурацию сервера в базу
     * @param config конфигурация сервера (список параметров,
     *               которые может отдавать сервер)
     * @param serverName имя сервера
     */
    void putConfig(List<String> config, String serverName);

    /**
     * Метод выгружает из базы список параметров для
     * получения по ним значений
     * @param objectName имя объекта
     * @param serverName имя сервера
     * @return список параметров
     */
    ArrayList<DataModel> loadObjectParameters(String objectName, String serverName);

    /**
     * Асинхронный метод для загрузки значений по парамтерам в базу
     * @param paramList список параметров со значениями
     * @return ничего не возращается
     */
    Future<Void> putData(List<DataModel> paramList);
}
