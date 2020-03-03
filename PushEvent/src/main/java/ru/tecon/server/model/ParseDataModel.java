package ru.tecon.server.model;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Класс данных для использования в разборе
 * архивных данных от pushEvents
 */
public class ParseDataModel implements Comparable<ParseDataModel> {

    private LocalDateTime date;
    private Map<String, Object> data;

    public ParseDataModel(LocalDateTime date, Map<String, Object> data) {
        this.date = date;
        this.data = data;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public Object getValue(String key) {
        return data.get(key);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ParseDataModel.class.getSimpleName() + "[", "]")
                .add("date=" + date)
                .add("data=" + data)
                .toString();
    }

    @Override
    public int compareTo(ParseDataModel o) {
        if (o.date == null) {
            return 1;
        }
        if (date == null) {
            return -1;
        }
        return date.compareTo(o.date);
    }
}
