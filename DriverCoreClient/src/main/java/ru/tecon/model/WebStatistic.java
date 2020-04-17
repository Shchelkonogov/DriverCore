package ru.tecon.model;

import java.io.Serializable;
import java.util.StringJoiner;

public class WebStatistic implements Serializable {

    private String serverName;
    private String ip;
    private String objectName;
    private String socketTime;
    private String socketCount;
    private String status;
    private String trafficTime;
    private String trafficIn;
    private String trafficOut;
    private String trafficDay;
    private String trafficMonth;

    public WebStatistic(String serverName, String ip, String objectName, String socketTime, String socketCount,
                        String status, String trafficTime, String trafficIn, String trafficOut, String trafficDay,
                        String trafficMonth) {
        this.serverName = serverName;
        this.ip = ip;
        this.objectName = objectName;
        this.socketTime = socketTime;
        this.socketCount = socketCount;
        this.status = status;
        this.trafficTime = trafficTime;
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

    public String getSocketTime() {
        return socketTime;
    }

    public String getSocketCount() {
        return socketCount;
    }

    public String getStatus() {
        return status;
    }

    public String getTrafficTime() {
        return trafficTime;
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

    public void setSocketTime(String socketTime) {
        this.socketTime = socketTime;
    }

    public void setSocketCount(String socketCount) {
        this.socketCount = socketCount;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setTrafficTime(String trafficTime) {
        this.trafficTime = trafficTime;
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
                .add("socketTime='" + socketTime + "'")
                .add("socketCount='" + socketCount + "'")
                .add("status='" + status + "'")
                .add("trafficTime='" + trafficTime + "'")
                .add("trafficIn='" + trafficIn + "'")
                .add("trafficOut='" + trafficOut + "'")
                .add("trafficDay='" + trafficDay + "'")
                .add("trafficMonth='" + trafficMonth + "'")
                .toString();
    }
}
