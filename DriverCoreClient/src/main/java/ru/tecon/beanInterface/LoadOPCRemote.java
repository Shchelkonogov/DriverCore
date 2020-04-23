package ru.tecon.beanInterface;

import ru.tecon.model.DataModel;
import ru.tecon.model.WebStatistic;

import javax.ejb.Remote;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    void putConfig(Set<String> config, String serverName);

    /**
     * Метод выгружает конфигурацию сервера в базу
     * @param config конфигурация сервера (список параметров,
     *               которые может отдавать сервер)
     * @param ipAddress ip адрес по которому грузилась мгновенная конфигурация
     * @param serverName имя сервера
     */
    void putConfig(Set<String> config, String ipAddress, String serverName);

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
     * Метод для загрузки мгновенных значений по парамтерам в базу
     * @param paramList список параметров со значениями
     */
    void putInstantData(List<DataModel> paramList);

    /**
     * Метод для проверки запроса от базы на выгрузку конфигурации.
     * Если есть запрос, то через websocket обращается к нужному серверу
     * и передает ему ip адрес для загрузки.
     * Если ip адрес не распознан, то запрос по webSocket не отправляется
     * @param serverName имя сервера
     */
    void checkConfigRequest(String serverName);

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

    /**
     * Метод выставляет статус не выполнения команды на получение мгновенных данных
     * @param path имя сервера_url
     * @param message сообщение
     */
    void errorExecuteAsyncRefreshCommand(String path, String message);

    /**
     * Метод на запрос статистики от подключенных серверов
     */
    void requestStatistic();

    /**
     * Метод выгружает на сервер статистику
     * @param statistic статистика
     */
    Future<Void> uploadStatistic(WebStatistic statistic);

    /**
     * Метод определяет возвращает имя объекта в системе по имени сервера и ip прибора
     * @param serverName имя сервера
     * @param ip ip прибора
     * @return имя объекта
     */
    String loadObjectName(String serverName, String ip);

    /**
     * Метод отправляет сообщение на изменение статуса обеъекта
     * Блокирует его или разблокирует
     * @param serverName имя сервера
     * @param ip ip прибора
     * @param status статус
     */
    void changeStatus(String serverName, String ip, boolean status);
}
