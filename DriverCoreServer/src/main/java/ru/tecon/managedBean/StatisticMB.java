package ru.tecon.managedBean;

import org.apache.poi.ss.usermodel.Workbook;
import org.primefaces.PrimeFaces;
import ru.tecon.ejb.WebConsoleBean;
import ru.tecon.model.Command;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Контроллер для jsf статистики работы серверов MFK1500
 */
@ManagedBean(name = "statisticBean")
@ViewScoped
public class StatisticMB implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(StatisticMB.class.getName());

    private String sessionID;

    private boolean admin = false;

    private List<WebStatistic> tableData = new ArrayList<>();
    private List<WebStatistic> filteredTableData = new ArrayList<>();
    private WebStatistic selectedRow;

    private List<ObjectInfoModel> infoTableData = new ArrayList<>();

    private String ip;
    private String serverName;

    private List<String> lastDataGroups = new ArrayList<>();

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
                 Workbook wb = WebStatisticReport.generateReport(filteredTableData)) {
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

        // Для тестирования. Отправка запроса на полную статистику удаленному серверу.
//        Hashtable<String, String> ht = new Hashtable<>();
//        ht.put(Context.INITIAL_CONTEXT_FACTORY, "weblogic.jndi.WLInitialContextFactory");
//        ht.put(Context.PROVIDER_URL, "t3://172.16.4.26:7001");
//
//        try {
//            Context ctx = new InitialContext(ht);
//
//            LoadOPCRemote remote = (LoadOPCRemote) ctx.lookup("ejb.LoadOPC#ru.tecon.beanInterface.LoadOPCRemote");
//
//            remote.requestStatistic();
//        } catch (NamingException e) {
//            e.printStackTrace();
//        }

    }

    /**
     * Устанавливаем последние группы данных полученных по контроллеру
     */
    public void setLogData() {
        String info = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("json");

        Jsonb json = JsonbBuilder.create();
        lastDataGroups = json.fromJson(info, new ArrayList<String>(){}.getClass().getGenericSuperclass());
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

        tableData.removeIf(statistic -> {
           for (WebStatistic newStatistic: newStatistics) {
               if (statistic.getServerName().equals(newStatistic.getServerName()) &&
                       statistic.getIp().equals(newStatistic.getIp())) {
                   return true;
               }
           }
           return false;
        });

        tableData.addAll(newStatistics);

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

        tableData.clear();
        webConsoleBean.produceMessage(new Command("requestStatistic"));
    }

    /**
     * Обработка запроса на выгрузку последних переданных груп данных от контроллера
     */
    public void requestLog() {
        lastDataGroups.clear();

        Command command = new Command("getLastConfigNames");
        command.addParameter("server", selectedRow.getServerName());
        command.addParameter("url", selectedRow.getIp());
        command.addParameter("sessionID", sessionID);

        webConsoleBean.produceMessage(command);
    }

    /**
     * Устанавливаем данные статистики полученные от контроллера (обновление одной строки статистики)
     */
    public void update() {
        String json = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("json");

        Jsonb jsonBuilder = JsonbBuilder.create();
        WebStatistic st = jsonBuilder.fromJson(json, WebStatistic.class);

        boolean contains = false;

        for (WebStatistic tableDatum: tableData) {
            if (tableDatum.getServerName().equals(st.getServerName()) &&
                    tableDatum.getIp().equals(st.getIp())) {
                List<String> update = new ArrayList<>();
                boolean filter = false;
                int index = filteredTableData.indexOf(tableDatum);

                if (!tableDatum.getObjectName().equals(st.getObjectName())) {
                    tableDatum.setObjectName(st.getObjectName());
                    update.add("tableForm:table:" + index + ":objectNameValue");
                    filter = true;
                }

                if (!tableDatum.getSocketCount().equals(st.getSocketCount())) {
                    tableDatum.setSocketCount(st.getSocketCount());
                    update.add("tableForm:table:" + index + ":socketCountValue");
                    filter = true;
                }

                if (!tableDatum.getStatus().equals(st.getStatus())) {
                    tableDatum.setStatus(st.getStatus());
                    update.add("tableForm:table:" + index + ":statusValue");
                    filter = true;
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
                    PrimeFaces.current().executeScript("PF('tableWidget').filter(); updateFooter();");
                } else {
                    if (index != -1) {
                        update.forEach(s -> PrimeFaces.current().ajax().update(s));
                    }
                }

                contains = true;
                break;
            }
        }

        if (!contains) {
            tableData.add(st);

            PrimeFaces.current().executeScript("PF('tableWidget').filter(); updateFooter();");
        }
    }

    public List<ObjectInfoModel> getInfoTableData() {
        return infoTableData;
    }

    public List<WebStatistic> getFilteredTableData() {
        return filteredTableData;
    }

    public void setFilteredTableData(List<WebStatistic> filteredTableData) {
        this.filteredTableData = filteredTableData;
    }

    public boolean isAdmin() {
        return admin;
    }

    public List<String> getLastDataGroups() {
        return lastDataGroups;
    }

    public String getIp() {
        return ip;
    }

    public String getServerName() {
        return serverName;
    }

    public List<WebStatistic> getTableData() {
        return tableData;
    }

    public WebStatistic getSelectedRow() {
        return selectedRow;
    }

    public void setSelectedRow(WebStatistic selectedRow) {
        this.selectedRow = selectedRow;
    }
}
