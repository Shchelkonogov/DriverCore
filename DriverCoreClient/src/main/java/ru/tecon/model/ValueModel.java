package ru.tecon.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.StringJoiner;

public class ValueModel implements Serializable {

    private String value;
    private LocalDateTime time;
    private int quality = 192;

    public ValueModel(String value, LocalDateTime time) {
        this.value = value;
        this.time = time;
    }

    public ValueModel(String value, LocalDateTime time, int quality) {
        this(value, time);
        this.quality = quality;
    }

    public String getValue() {
        return value;
    }

    void setValue(String value) {
        this.value = value;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public int getQuality() {
        return quality;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ValueModel.class.getSimpleName() + "[", "]")
                .add("value='" + value + "'")
                .add("time=" + time)
                .add("quality=" + quality)
                .toString();
    }
}
