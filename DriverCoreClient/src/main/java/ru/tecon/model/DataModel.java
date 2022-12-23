package ru.tecon.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Модель данных для загрузки значений в базу.
 * Включает в себя имя параметра.
 * id объекта, id параметра, id агрегата,
 * дата и время с которого требуются данные для базы,
 * массив для хранения полученных значений.
 * А так же поле для перевода значений в другую величину
 * (в данной версси реализовано исклучительно метод умножения)
 */
public class DataModel implements Comparable<DataModel>, Serializable {

    private String paramName;
    private int objectId;
    private int paramId;
    private int aggregateId;
    private LocalDateTime startTime;
    private List<ValueModel> data = new ArrayList<>();
    private String incrementValue;

    public DataModel(DataModel dataModel) {
        this(dataModel.getParamName(), dataModel.getObjectId(),
                dataModel.getParamId(), dataModel.getAggregateId(),
                dataModel.getStartTime(), dataModel.getIncrementValue());
        data = new ArrayList<>();
    }

    public DataModel(String paramName, int objectId, int paramId, int aggregateId,
                     LocalDateTime startTime, String incrementValue) {
        this.paramName = paramName;
        this.objectId = objectId;
        this.paramId = paramId;
        this.aggregateId = aggregateId;
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

    public int getAggregateId() {
        return aggregateId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public List<ValueModel> getData() {
        return data;
    }

    private String getIncrementValue() {
        return incrementValue;
    }

    @Override
    public String toString() {
        return "DataModel{" + "paramName='" + paramName + '\'' +
                ", objectId=" + objectId +
                ", paramId=" + paramId +
                ", aggregateId=" + aggregateId +
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
