package ru.tecon.report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFFont;
import ru.tecon.model.WebStatistic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Класс для формирования excel отчета по данным web статистики
 * @author Maksim Shchelkonogov
 */
public class WebStatisticReport {

    private static final String[] HEAD = {"№", "имя сервера", "ip адрес", "имя объекта", "сокет", "статус",
            "сеанс связи", "входящий", "исходящий", "общий суточный", "общий месячный"};

    /**
     * Метод формирует отчет по статистике
     * @param statisticList данные для отчета
     * @return excel отчет
     */
    public static Workbook generateReport(List<WebStatistic> statisticList) {
        SXSSFWorkbook wb = new SXSSFWorkbook();
        SXSSFSheet sheet = wb.createSheet("Статистика");

        Map<String, CellStyle> styleMap = createStyles(wb);

        sheet.setColumnWidth(0, 2 * 256);

        for (int i = 0; i < HEAD.length; i++) {
            sheet.trackColumnForAutoSizing(i + 1);
        }

        Row row = sheet.createRow(1);
        Cell cell = row.createCell(1);
        cell.setCellValue("Статистика работы контроллеров MFK1500");
        cell.setCellStyle(styleMap.get("header"));
        CellRangeAddress cellAddresses = new CellRangeAddress(1, 1, 1, HEAD.length);
        sheet.addMergedRegion(cellAddresses);

        createRow(sheet.createRow(3), styleMap.get("tableHeader"), HEAD);

        int index = 4;
        for (WebStatistic statistic: statisticList) {
            createRow(sheet.createRow(index), styleMap.get("cell"), String.valueOf(index - 3), statistic.getServerName(), statistic.getIp(),
                    statistic.getObjectName(), statistic.getSocketCount(), statistic.getStatus(), statistic.getLastRequestTime(),
                    statistic.getTrafficIn(), statistic.getTrafficOut(), statistic.getTrafficDay(), statistic.getTrafficMonth());

            index++;
        }

        for (int i = 0; i < HEAD.length; i++) {
            sheet.autoSizeColumn(i + 1);
        }

        return wb;
    }

    private static void createRow(Row row, CellStyle style, String... items) {
        Cell cell;
        for (int i = 0; i < items.length; i++) {
            cell = row.createCell(i + 1);
            cell.setCellValue(items[i]);
            cell.setCellStyle(style);
        }
    }

    private static Map<String, CellStyle> createStyles(Workbook wb) {
        Map<String, CellStyle> styles = new HashMap<>();

        XSSFFont font14 = (XSSFFont) wb.createFont();
        font14.setBold(true);
        font14.setFontHeight(14);

        XSSFFont font16 = (XSSFFont) wb.createFont();
        font16.setBold(true);
        font16.setFontHeight(16);

        CellStyle style = wb.createCellStyle();
        style.setFont(font16);
        style.setAlignment(HorizontalAlignment.CENTER);

        styles.put("header", style);

        style = wb.createCellStyle();
        style.setFont(font14);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderLeft(BorderStyle.MEDIUM);
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setBorderRight(BorderStyle.MEDIUM);
        style.setBorderTop(BorderStyle.MEDIUM);

        styles.put("tableHeader", style);

        style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);

        styles.put("cell", style);

        return styles;
    }
}
