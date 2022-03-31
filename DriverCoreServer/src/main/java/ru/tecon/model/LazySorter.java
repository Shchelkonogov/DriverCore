package ru.tecon.model;

import org.primefaces.model.SortOrder;

import java.lang.reflect.Field;
import java.util.Comparator;

/**
 * Сортировка веб статистики
 * @author Maksim Shchelkonogov
 */
public class LazySorter implements Comparator<WebStatistic> {

    private String sortField;

    private SortOrder sortOrder;

    public LazySorter(String sortField, SortOrder sortOrder) {
        this.sortField = sortField;
        this.sortOrder = sortOrder;
    }

    @Override
    public int compare(WebStatistic prop1, WebStatistic prop2) {
        try {
            Field field = WebStatistic.class.getDeclaredField(this.sortField);
            field.setAccessible(true);

            Object value1 = field.get(prop1);
            Object value2 = field.get(prop2);

            int value = ((Comparable) value1).compareTo(value2);

            return SortOrder.ASCENDING.equals(sortOrder) ? value : -1 * value;
        }
        catch(Exception e) {
            throw new RuntimeException();
        }
    }
}
