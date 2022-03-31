package ru.tecon.traffic;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Последние полученные значения
 * @author Maksim Shchelkonogov
 */
public class LastDayData implements Serializable {

    private AtomicInteger count = new AtomicInteger(1);
    private LocalDateTime dateTime = LocalDateTime.now();
    private List<Object> data = new ArrayList<>();

    public LastDayData() {
    }

    public LastDayData(AtomicInteger count, LocalDateTime dateTime, List<Object> data) {
        this.count = count;
        this.dateTime = dateTime;
        this.data = data;
    }

    public AtomicInteger getCount() {
        return count;
    }

    public void setCount(AtomicInteger count) {
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
        return new StringJoiner(", ", LastDayData.class.getSimpleName() + "[", "]")
                .add("count=" + count)
                .add("dateTime=" + dateTime)
                .add("data=" + data)
                .toString();
    }
}
