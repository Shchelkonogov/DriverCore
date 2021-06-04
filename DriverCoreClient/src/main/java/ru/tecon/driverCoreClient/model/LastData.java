package ru.tecon.driverCoreClient.model;

import java.io.Serializable;
import java.util.StringJoiner;

/**
 * @author Maksim Shchelkonogov
 * Класс описывающий последние данные от MFK1500
 */
public class LastData implements Serializable {

    private String identifier;
    private String groupName;
    private int count;

    public LastData() {
    }

    public LastData(String identifier, String groupName, int count) {
        this.identifier = identifier;
        this.groupName = groupName;
        this.count = count;
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

    @Override
    public String toString() {
        return new StringJoiner(", ", LastData.class.getSimpleName() + "[", "]")
                .add("identifier='" + identifier + "'")
                .add("groupName='" + groupName + "'")
                .add("count=" + count)
                .toString();
    }
}
