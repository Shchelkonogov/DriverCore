package ru.tecon.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DataModel implements Comparable<DataModel>, Serializable {

    private String paramName;
    private int objectId;
    private int paramId;
    private int aggrId;
    private LocalDateTime startTime;
    private List<ValueModel> data = new ArrayList<>();
    private String incrementValue;

    public DataModel(String paramName, int objectId, int paramId, int aggrId, LocalDateTime startTime, String incrementValue) {
        this.paramName = paramName;
        this.objectId = objectId;
        this.paramId = paramId;
        this.aggrId = aggrId;
        this.startTime = startTime;
        this.incrementValue = incrementValue;
    }

    public void addData(ValueModel item) {
        if ((incrementValue != null) && (item != null)) {
            item.setValue(new BigDecimal(item.getValue()).multiply(new BigDecimal(incrementValue)).toString());
        }
        data.add(item);
    }

    public String getParamName() {
        return paramName;
    }

    public int getObjectId() {
        return objectId;
    }

    public int getParamId() {
        return paramId;
    }

    public int getAggrId() {
        return aggrId;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public List<ValueModel> getData() {
        return data;
    }

    @Override
    public String toString() {
        return "DataModel{" + "paramName='" + paramName + '\'' +
                ", objectId=" + objectId +
                ", paramId=" + paramId +
                ", aggrId=" + aggrId +
                ", startTime=" + startTime +
                ", data=" + data +
                '}';
    }

    @Override
    public int compareTo(DataModel o) {
        if (o.startTime == null) {
            return 1;
        }
        if (startTime == null) {
            return -1;
        }
        return startTime.compareTo(o.startTime);
    }
}
