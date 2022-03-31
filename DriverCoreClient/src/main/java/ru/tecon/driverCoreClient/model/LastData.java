package ru.tecon.driverCoreClient.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.StringJoiner;

/**
 * @author Maksim Shchelkonogov
 * Класс описывающий последние данные от MFK1500
 */
public class LastData implements Serializable {

    private String identifier;
    private String groupName;
    private int count;
    private LocalDateTime dateTime;
    private List<Object> data;

    public LastData() {
    }

    public LastData(String identifier, String groupName, int count, LocalDateTime dateTime, List<Object> data) {
        this.identifier = identifier;
        this.groupName = groupName;
        this.count = count;
        this.dateTime = dateTime;
        this.data = data;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public List<Object> getData() {
        return data;
    }

    public void setData(List<Object> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", LastData.class.getSimpleName() + "[", "]")
                .add("identifier='" + identifier + "'")
                .add("groupName='" + groupName + "'")
                .add("count=" + count)
                .add("dateTime=" + dateTime)
                .add("data=" + data)
                .toString();
    }
}
