package ru.tecon.managedBean;

import ru.tecon.beanInterface.LoadOPCLocal;
import ru.tecon.model.WebStatistic;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

@ManagedBean(name = "statisticBean")
@ViewScoped
public class StatisticMB implements Serializable {

    private List<WebStatistic> tableData = new ArrayList<>();

    @EJB
    private LoadOPCLocal bean;

    @PostConstruct
    private void init() {
        bean.requestStatistic();
    }

    public List<WebStatistic> getTableData() {
        return tableData;
    }

    public void update() {
        String json = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("json");

        System.out.println(json);

        JsonReader jsonReader = Json.createReader(new StringReader(json));
        JsonObject jsonObject = jsonReader.readObject();

        WebStatistic st = new WebStatistic(jsonObject.getString("serverName"), jsonObject.getString("ip"),
                jsonObject.getString("objectName"), jsonObject.getString("socketTime"),
                jsonObject.getString("socketCount"), jsonObject.getString("status"),
                jsonObject.getString("trafficTime"), jsonObject.getString("trafficIn"),
                jsonObject.getString("trafficOut"), jsonObject.getString("trafficDay"),
                jsonObject.getString("trafficMonth"));

        boolean contains = false;

        for (WebStatistic tableDatum : tableData) {
            if (tableDatum.getServerName().equals(st.getServerName()) &&
                    tableDatum.getIp().equals(st.getIp())) {
                tableDatum.setSocketTime(st.getSocketTime());
                tableDatum.setSocketCount(st.getSocketCount());
                tableDatum.setStatus(st.getStatus());
                tableDatum.setTrafficTime(st.getTrafficTime());
                tableDatum.setTrafficIn(st.getTrafficIn());
                tableDatum.setTrafficOut(st.getTrafficOut());
                tableDatum.setTrafficDay(st.getTrafficDay());
                tableDatum.setTrafficMonth(st.getTrafficMonth());
                contains = true;
                break;
            }
        }

        if (!contains) {
            tableData.add(st);
        }
    }
}
