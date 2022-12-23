package ru.tecon.model;

import org.primefaces.model.FilterMeta;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortMeta;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Класс для ленивой загрузки статистики
 * @author Maksim Shchelkonogov
 */
public class LazyCustomDataModel<T extends WebStatistic> extends LazyDataModel<T> {

    private List<T> dataSource = new ArrayList<>();
    private List<T> filteredData = new ArrayList<>();
    private List<T> filteredAndPaginateData = new ArrayList<>();

    public LazyCustomDataModel() {
    }

    @Override
    public T getRowData(String rowKey) {
        for (T prop: dataSource) {
            if (prop.getRowId().equals(rowKey)) {
                return prop;
            }
        }
        return null;
    }

    @Override
    public String getRowKey(T object) {
        return object.getRowId();
    }

    @Override
    public List<T> load(int first, int pageSize, Map<String, SortMeta> sortBy, Map<String, FilterMeta> filterBy) {
        List<T> data = new ArrayList<>();

        //filter
        for (T prop: dataSource) {
            boolean match = true;

            if (filterBy != null) {
                for (FilterMeta meta: filterBy.values()) {
                    try {
                        String filterField = meta.getFilterField();
                        Object filterValue = meta.getFilterValue();

                        Field field = WebStatistic.class.getDeclaredField(filterField);
                        field.setAccessible(true);
                        String fieldValue = String.valueOf(field.get(prop));

                        if ((filterValue == null) || fieldValue.contains(filterValue.toString())) {
                            match = true;
                        } else {
                            match = false;
                            break;
                        }
                    } catch (Exception e) {
                        match = false;
                    }
                }
            }

            if (match) {
                data.add(prop);
            }
        }

        //sort
        if ((sortBy != null) && !sortBy.isEmpty()) {
            for (SortMeta meta: sortBy.values()) {
                data.sort(new LazySorter(meta.getSortField(), meta.getSortOrder()));
            }
        }

        //rowCount
        int dataSize = data.size();
        this.setRowCount(dataSize);

        filteredData = data;

        //paginate
        if (dataSize > pageSize) {
            try {
                filteredAndPaginateData = data.subList(first, first + pageSize);
            } catch (IndexOutOfBoundsException e) {
                filteredAndPaginateData = data.subList(first, first + (dataSize % pageSize));
            }
        } else {
            filteredAndPaginateData = data;
        }

        return filteredAndPaginateData;
    }

    /**
     * @return фильтрованные данные
     */
    public List<T> getFilteredData() {
        return filteredData;
    }

    /**
     * @return фильтрованные и разбитые на страницы данные
     */
    public List<T> getFilteredAndPaginateData() {
        return filteredAndPaginateData;
    }

    /**
     * @return все данные
     */
    public List<T> getDataSource() {
        return dataSource;
    }
}
