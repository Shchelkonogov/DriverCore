package ru.tecon.model;

import java.io.Serializable;
import java.util.StringJoiner;
import java.util.UUID;

public class WebStatistic implements Serializable {

    private String rowId;
    private String serverName;
    private String ip;
    private String objectName;
    private String socketCount;
    private String status;
    private String lastRequestTime;
    private String trafficIn;
    private String trafficOut;
    private String trafficDay;
    private String trafficMonth;

    public WebStatistic(String serverName, String ip, String objectName, String socketCount,
                        String status, String lastRequestTime, String trafficIn, String trafficOut, String trafficDay,
                        String trafficMonth) {
        rowId = UUID.randomUUID().toString();
        this.serverName = serverName;
        this.ip = ip;
        this.objectName = objectName;
        this.socketCount = socketCount;
        this.status = status;
        this.lastRequestTime = lastRequestTime;
        this.trafficIn = trafficIn;
        this.trafficOut = trafficOut;
        this.trafficDay = trafficDay;
        this.trafficMonth = trafficMonth;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getServerName() {
        return serverName;
    }

    public String getIp() {
        return ip;
    }

    public String getSocketCount() {
        return socketCount;
    }

    public String getStatus() {
        return status;
    }

    public String getLastRequestTime() {
        return lastRequestTime;
    }

    public String getTrafficIn() {
        return trafficIn;
    }

    public String getTrafficOut() {
        return trafficOut;
    }

    public String getTrafficDay() {
        return trafficDay;
    }

    public String getTrafficMonth() {
        return trafficMonth;
    }

    public String getRowId() {
        return rowId;
    }

    public void setSocketCount(String socketCount) {
        this.socketCount = socketCount;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setLastRequestTime(String lastRequestTime) {
        this.lastRequestTime = lastRequestTime;
    }

    public void setTrafficIn(String trafficIn) {
        this.trafficIn = trafficIn;
    }

    public void setTrafficOut(String trafficOut) {
        this.trafficOut = trafficOut;
    }

    public void setTrafficDay(String trafficDay) {
        this.trafficDay = trafficDay;
    }

    public void setTrafficMonth(String trafficMonth) {
        this.trafficMonth = trafficMonth;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", WebStatistic.class.getSimpleName() + "[", "]")
                .add("serverName='" + serverName + "'")
                .add("ip='" + ip + "'")
                .add("objectName='" + objectName + "'")
                .add("socketCount='" + socketCount + "'")
                .add("status='" + status + "'")
                .add("lastRequestTime='" + lastRequestTime + "'")
                .add("trafficIn='" + trafficIn + "'")
                .add("trafficOut='" + trafficOut + "'")
                .add("trafficDay='" + trafficDay + "'")
                .add("trafficMonth='" + trafficMonth + "'")
                .toString();
    }
}
