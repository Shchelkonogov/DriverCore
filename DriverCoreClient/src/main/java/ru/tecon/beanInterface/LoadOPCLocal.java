package ru.tecon.beanInterface;

import ru.tecon.model.DataModel;

import javax.ejb.Local;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Local интерфейс для работы с основными функциями
 * общения драйвера с базой
 */
@Local
public interface LoadOPCLocal {

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
     * Метод выгружает конфигурацию сервера в базу
     * @param config конфигурация сервера (список параметров,
     *               которые может отдавать сервер)
     * @param instantConfig конфигурация мгновенных данный от приборов
     * @param serverName имя сервера
     */
    void putConfig(List<String> config, Map<String, List<String>> instantConfig, String serverName);

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

    /**
     * Метод для загрузки значений по параметрам в базу
     * Метод сначала досчитывает интеграторы а затем вызывает метод {@link #putData(List)}
     * Если имя параметра вида ...:...:... подсчет ведется если последний блок после :
     * цифра 5, 6 или 7. Во всех вариантах значение складывается с предыдущем значением.
     * В случае 6 текущее значение умножается на 60 (перевод из минут в секунды)
     * В случае 7 текущее значение умножается на 3600 (перевод из часов в секунды)
     * @param paramList список параметров со значениями
     */
    void putDataWithCalculateIntegrator(List<DataModel> paramList);

    /**
     * Метод для выгрузки списка URL по которым база запросила конфигурацию сервера
     * @param serverName имя сервера
     * @return список URL
     */
    ArrayList<String> getURLToLoadConfig(String serverName);

    /**
     * Метод для проверки запроса от базы на мгновенные данные.
     * Если есть запрос, то через websocket обращается к нужному серверу
     * @param serverName имя сервера
     */
    void checkInstantRequest(String serverName);

    /**
     * Метод выгружает из базы список мгновенных параметров для
     * получения по ним значений
     * @param serverName имя сервера
     * @param url url контроллера
     * @return список параметров
     */
    ArrayList<DataModel> loadObjectInstantParameters(String serverName, String url);
}
