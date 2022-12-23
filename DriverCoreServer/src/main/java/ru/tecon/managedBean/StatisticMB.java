package ru.tecon.managedBean;

import org.apache.poi.ss.usermodel.Workbook;
import org.primefaces.PrimeFaces;
import org.primefaces.component.datatable.DataTable;
import org.primefaces.event.SelectEvent;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.LazyDataModel;
import ru.tecon.driverCoreClient.model.LastData;
import ru.tecon.ejb.WebConsoleBean;
import ru.tecon.model.Command;
import ru.tecon.model.LazyCustomDataModel;
import ru.tecon.model.ObjectInfoModel;
import ru.tecon.model.WebStatistic;
import ru.tecon.report.WebStatisticReport;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

// TODO Добавить кнопку запросить конфигурацию
//  При первоначальной загрузки страници сделать крутилку (подключено 3 сервера жду данные от серверов, сервер 1 загружен и тд.)
//  И потом уже отображать данные, так же не обновлять данные по серверам, которые первоначально не были загружены.
//  В колонке сервера поставить фильтр по выпадающему меню с существующими серверами
//  Когда убирается сервер из прикрепленных, то из морды удалять его данные
//  Добавить кнопку скинуть метку, добавить кнопку скинуть модель объекта

/**
 * Контроллер для jsf статистики работы серверов MFK1500
 */
@ManagedBean(name = "statisticBean")
@ViewScoped
public class StatisticMB implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(StatisticMB.class.getName());

    private String sessionID;

    private boolean admin = false;

    private DataTable table;
    private LazyCustomDataModel<WebStatistic> tableData = new LazyCustomDataModel<>();
    private WebStatistic selectedRow;

    private List<ObjectInfoModel> infoTableData = new ArrayList<>();

    private String ip;
    private String serverName;

    private List<LastData> lastDataGroups = new ArrayList<>();
    private LastData selectedLastDataGroup;

    private List<String> configNames = new ArrayList<>();

    @EJB
    private WebConsoleBean webConsoleBean;

    @PostConstruct
    public void init() {
        String admParameter = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("adm");

        if (Objects.nonNull(admParameter) && admParameter.equalsIgnoreCase("true")) {
            admin = true;
        }
    }

    /**
     * Метод обрабатывает запрос выгрузки статистики в excel
     */
    public void createReport() {
        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();

        try {
            ec.responseReset();
            ec.setResponseContentType("application/vnd.ms-excel; charset=UTF-8");
            ec.setResponseHeader("Content-Disposition", "attachment; filename=\"" +
                    URLEncoder.encode("Статистика", "UTF-8") + " " + URLEncoder.encode("MFK1500.xlsx", "UTF-8") + "\"");
            ec.setResponseCharacterEncoding("UTF-8");

            try (OutputStream outputStream = ec.getResponseOutputStream();
                 Workbook wb = WebStatisticReport.generateReport(tableData.getFilteredData())) {
                wb.write(outputStream);
                outputStream.flush();
            } catch (IOException e) {
                LOGGER.warning("error send report " + e.getMessage());
            }
        } catch (UnsupportedEncodingException e) {
            LOGGER.warning("encoding error " + e.getMessage());
        }

        fc.responseComplete();
    }

    /**
     * Метод обрабатывает запрос на изменения статуса блокировки объекта
     * @param value блокировать/разблокировать
     */
    public void changeStatus(boolean value) {
        Command command = new Command();
        command.addParameter("server", selectedRow.getServerName());
        command.addParameter("url", selectedRow.getIp());

        if (value) {
            command.setName("block");
        } else {
            command.setName("unblock");
        }

        webConsoleBean.produceMessage(command);
    }

    /**
     * Метод переподписывает объект и отправляет запрос на снятие блокировки
     */
    public void resignObject() {
        webConsoleBean.ResignObject(selectedRow.getObjectName());
        changeStatus(false);
    }

    /**
     * Запрос на информацию о контроллере
     * @param ip адрес контролера
     * @param serverName сервер приема данных с контроллера
     */
    public void requestInfo(String ip, String serverName) {
        infoTableData.clear();

        this.ip = ip;
        this.serverName = serverName;

        Command command = new Command("info");
        command.addParameter("server", serverName);
        command.addParameter("url", ip);
        command.addParameter("sessionID", sessionID);

        webConsoleBean.produceMessage(command);
    }

    /**
     * Устанавливаем id webSocket сессии
     */
    public void setID() {
        sessionID = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("json");

        Command command = new Command("requestStatistic");
        command.addParameter("sessionID", sessionID);

        webConsoleBean.produceMessage(command);
    }

    /**
     * Устанавливаем последние группы данных полученных по контроллеру
     */
    public void setLogData() {
        String info = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("json");

        Jsonb json = JsonbBuilder.create();
        lastDataGroups = json.fromJson(info, new ArrayList<LastData>(){}.getClass().getGenericSuperclass());
    }

    /**
     * Метод обновляет список параметров группы данных
     */
    public void updateConfigNames() {
        String info = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("json");

        Jsonb json = JsonbBuilder.create();
        configNames = json.fromJson(info, new ArrayList<String>(){}.getClass().getGenericSuperclass());

        if (configNames.size() == selectedLastDataGroup.getData().size()) {
            for (int i = 0; i < configNames.size(); i++) {
                configNames.set(i, configNames.get(i) + " / " + selectedLastDataGroup.getData().get(i));
            }
        }
    }

    /**
     * Устанавливаем информацию о контроллере
     */
    public void updateInfo() {
        String info = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("json");

        Jsonb json = JsonbBuilder.create();
        infoTableData = json.fromJson(info, new ArrayList<ObjectInfoModel>(){}.getClass().getGenericSuperclass());
    }

    /**
     * Отправка запроса на запись новых значений в контроллер
     */
    public void writeValues() {
        StringBuilder sb = new StringBuilder();
        infoTableData.forEach(v -> {
            if (v.isWrite()) {
                sb.append(v.getName()).append(":").append(v.getValue()).append(";");
            }
        });

        Command command = new Command("writeInfo");
        command.addParameter("server", serverName);
        command.addParameter("url", ip);
        command.addParameter("info", sb.toString());

        webConsoleBean.produceMessage(command);
    }

    /**
     * Отправляем запрос на синхронизацию времени на контроллере
     */
    public void synchronizeDate() {
        Command command = new Command("synchronizeDate");
        command.addParameter("server", serverName);
        command.addParameter("url", ip);

        webConsoleBean.produceMessage(command);
    }

    /**
     * Отправляем на контроллер запрос на изменение статуса проверки трафика на котроллере
     * @param ignoreTraffic сичтать трафик/не считать трафик
     */
    public void setIgnoreTraffic(boolean ignoreTraffic) {
        Command command = new Command();
        command.addParameter("server", selectedRow.getServerName());
        command.addParameter("url", selectedRow.getIp());

        if (ignoreTraffic) {
            command.setName("unblockTraffic");
        } else {
            command.setName("blockTraffic");
        }

        webConsoleBean.produceMessage(command);
    }

    /**
     * Устанавливаем данные статистики полученные от контроллеров (загрузка всей статистики)
     */
    public void updateAll() {
        String json = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("json");

        Jsonb jsonBuilder = JsonbBuilder.create();
        List<WebStatistic> newStatistics = jsonBuilder.fromJson(json, new ArrayList<WebStatistic>(){}.getClass().getGenericSuperclass());

        tableData.getDataSource().removeIf(statistic -> {
           for (WebStatistic newStatistic: newStatistics) {
               if (statistic.getServerName().equals(newStatistic.getServerName()) &&
                       statistic.getIp().equals(newStatistic.getIp())) {
                   return true;
               }
           }
           return false;
        });

        tableData.getDataSource().addAll(newStatistics);

        PrimeFaces.current().executeScript("PF('tableWidget').filter(); updateFooter();");
    }

    /**
     * Обработка запроса на удаление объекта
     */
    public void removeObject() {
        Command command = new Command("remove");
        command.addParameter("server", selectedRow.getServerName());
        command.addParameter("url", selectedRow.getIp());

        webConsoleBean.produceMessage(command);

        ((LazyCustomDataModel) tableData).getDataSource().remove(selectedRow);

        PrimeFaces.current().executeScript("PF('tableWidget').filter(); updateFooter();");
    }

    public void clearMark() {
        Command command = new Command("clearMark");
        command.addParameter("server", selectedRow.getServerName());
        command.addParameter("url", selectedRow.getIp());

        webConsoleBean.produceMessage(command);
    }

    public void clearObjectModel() {
        Command command = new Command("clearModel");
        command.addParameter("server", selectedRow.getServerName());
        command.addParameter("url", selectedRow.getIp());

        webConsoleBean.produceMessage(command);
    }

    /**
     * Обработка запроса на выгрузку последних переданных груп данных от контроллера
     */
    public void requestLog() {
        // TODO Реализовать крутилочку, что бы было понятно ожидание
        Command command = new Command("getLastConfigNames");
        command.addParameter("server", selectedRow.getServerName());
        command.addParameter("url", selectedRow.getIp());
        command.addParameter("sessionID", sessionID);

        webConsoleBean.produceMessage(command);
    }

    /**
     * Обработка выбора строки в таблице последних групп данных
     * @param event информация о выбранной строке
     */
    public void onRowSelect(SelectEvent<LastData> event) {
        // TODO Реализовать крутилочку, что бы было понятно ожидание
        String[] split = event.getObject().getIdentifier().split(":");

        Command command = new Command("getConfigGroup");
        command.addParameter("server", selectedRow.getServerName());
        command.addParameter("sessionID", sessionID);
        command.addParameter("bufferNumber", split[0]);
        command.addParameter("eventCode", split[1]);
        command.addParameter("size", split[2]);

        webConsoleBean.produceMessage(command);
    }

    /**
     * Метод обрабатывает закрытие dialog окна последние данные
     */
    public void onClose() {
        selectedLastDataGroup = null;
        lastDataGroups.clear();
        configNames.clear();
    }

    /**
     * Устанавливаем данные статистики полученные от контроллера (обновление одной строки статистики)
     */
    public void update() {
        String json = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("json");

        Jsonb jsonBuilder = JsonbBuilder.create();
        WebStatistic st = jsonBuilder.fromJson(json, WebStatistic.class);

        boolean contains = false;

        for (WebStatistic tableDatum: tableData.getDataSource()) {
            if (tableDatum.getServerName().equals(st.getServerName()) &&
                    tableDatum.getIp().equals(st.getIp())) {
                List<String> update = new ArrayList<>();
                boolean filter = false;
                int index = tableData.getFilteredAndPaginateData().indexOf(tableDatum);

                String sortField = table.getSortField() == null ? "objectName" : table.getSortField();
                Map<String, String> filterData = table.getFilterBy()
                        .values()
                        .stream()
                        .filter(filterMeta -> Objects.nonNull(filterMeta.getFilterValue()))
                        .collect(Collectors.toMap(FilterMeta::getFilterField, v -> v.getFilterValue().toString()));

                if (!tableDatum.getObjectName().equals(st.getObjectName())) {
                    tableDatum.setObjectName(st.getObjectName());
                    if ((sortField.equals("objectName") && (index != -1)) ||
                            (filterData.keySet().contains("objectName") && st.getObjectName().contains(filterData.get("objectName")))) {
                        filter = true;
                    } else {
                        update.add("tableForm:table:" + index + ":objectNameValue");
                    }
                }

                if (!tableDatum.getSocketCount().equals(st.getSocketCount()) || (tableDatum.isClosed() != st.isClosed())) {
                    tableDatum.setSocketCount(st.getSocketCount());
                    tableDatum.setClosed(st.isClosed());
                    if (sortField.equals("socketCount") && (index != -1)) {
                        filter = true;
                    } else {
                        update.add("tableForm:table:" + index + ":socketCountValue");
                    }
                }

                if (!tableDatum.getStatus().equals(st.getStatus())) {
                    tableDatum.setStatus(st.getStatus());
                    if (sortField.equals("status") && (index != -1)) {
                        filter = true;
                    } else {
                        update.add("tableForm:table:" + index + ":statusValue");
                    }
                }

                if (!tableDatum.getLastRequestTime().equals(st.getLastRequestTime())) {
                    tableDatum.setLastRequestTime(st.getLastRequestTime());
                    update.add("tableForm:table:" + index + ":lastRequestTimeValue");
                }

                if (!tableDatum.getTrafficIn().equals(st.getTrafficIn())) {
                    tableDatum.setTrafficIn(st.getTrafficIn());
                    update.add("tableForm:table:" + index + ":trafficInValue");
                }

                if (!tableDatum.getTrafficOut().equals(st.getTrafficOut())) {
                    tableDatum.setTrafficOut(st.getTrafficOut());
                    update.add("tableForm:table:" + index + ":trafficOutValue");
                }

                if (!tableDatum.getTrafficDay().equals(st.getTrafficDay())) {
                    tableDatum.setTrafficDay(st.getTrafficDay());
                    update.add("tableForm:table:" + index + ":trafficDayValue");
                }

                if (!tableDatum.getTrafficMonth().equals(st.getTrafficMonth())) {
                    tableDatum.setTrafficMonth(st.getTrafficMonth());
                    update.add("tableForm:table:" + index + ":trafficMonthValue");
                }

                if (filter) {
                    PrimeFaces.current().executeScript("PF('tableWidget').filter();");
                } else {
                    if (index != -1) {
                        PrimeFaces.current().ajax().update(update);
                    }
                }

                contains = true;
                break;
            }
        }

        if (!contains) {
            tableData.getDataSource().add(st);

            PrimeFaces.current().executeScript("PF('tableWidget').filter(); updateFooter();");
        }
    }

    public List<String> getConfigNames() {
        return configNames;
    }

    public List<ObjectInfoModel> getInfoTableData() {
        return infoTableData;
    }

    public boolean isAdmin() {
        return admin;
    }

    public List<LastData> getLastDataGroups() {
        return lastDataGroups;
    }

    public String getIp() {
        return ip;
    }

    public String getServerName() {
        return serverName;
    }

    public DataTable getTable() {
        return table;
    }

    public void setTable(DataTable table) {
        this.table = table;
    }

    public LazyDataModel<WebStatistic> getTableData() {
        return tableData;
    }

    public int getTableDataSize() {
        return tableData.getDataSource().size();
    }

    public int getIndex(WebStatistic object) {
        return tableData.getFilteredData().indexOf(object) + 1;
    }

    public WebStatistic getSelectedRow() {
        return selectedRow;
    }

    public void setSelectedRow(WebStatistic selectedRow) {
        this.selectedRow = selectedRow;
    }

    public LastData getSelectedLastDataGroup() {
        return selectedLastDataGroup;
    }

    public void setSelectedLastDataGroup(LastData selectedLastDataGroup) {
        this.selectedLastDataGroup = selectedLastDataGroup;
    }

    public String getSelectedLastDataGroupName() {
        return selectedLastDataGroup.getGroupName() + " " +
                selectedLastDataGroup.getDateTime().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")) + " UTC";
    }

    public String getCurrentDate() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }
}
