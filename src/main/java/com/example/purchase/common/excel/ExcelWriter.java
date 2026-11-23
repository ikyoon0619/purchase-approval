package com.example.purchase.common.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 목록을 .xlsx로 쓴다. 화면마다 열 정의(ExcelColumn)만 바꿔서 재사용한다.
 *
 * <p>SXSSFWorkbook: 메모리에는 최근 100행만 두고 나머지는 임시 파일로 흘려 쓴다.
 * XSSFWorkbook은 모든 행을 메모리에 들고 있어서 수만 행이면 OutOfMemoryError가 날 수 있다.
 */
public final class ExcelWriter {

    private static final int ROW_WINDOW = 100;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ExcelWriter() {
    }

    public static <T> void write(String sheetName, List<ExcelColumn<T>> columns, List<T> rows, OutputStream out)
            throws IOException {
        // try-with-resources: 끝나면 close()가 임시 파일까지 지운다 (POI 5.x)
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_WINDOW)) {
            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle headerStyle = headerStyle(workbook);

            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns.get(i).header());
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, columns.get(i).width() * 256 * 2); // 한글은 한 글자가 영문 2자 폭
            }
            sheet.createFreezePane(0, 1); // 스크롤해도 머리글이 보이게

            int rowIndex = 1;
            for (T item : rows) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < columns.size(); i++) {
                    setValue(row.createCell(i), columns.get(i).value().apply(item));
                }
            }
            workbook.write(out);
        }
    }

    private static void setValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number n && !(value instanceof BigDecimal)) {
            cell.setCellValue(n.doubleValue());
        } else if (value instanceof BigDecimal d) {
            cell.setCellValue(d.doubleValue());
        } else if (value instanceof Boolean b) {
            cell.setCellValue(b ? "Y" : "N");
        } else if (value instanceof LocalDateTime t) {
            cell.setCellValue(t.format(DATE_TIME));
        } else if (value instanceof LocalDate d) {
            cell.setCellValue(d.toString());
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }
}
