package com.queryzen.engine;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ExportService {

    public byte[] toXlsx(QueryResult result) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            Sheet sheet = wb.createSheet("query result");

            Row header = sheet.createRow(0);
            List<QueryResult.Column> columns = result.columns();
            for (int i = 0; i < columns.size(); i++) {
                header.createCell(i).setCellValue(columns.get(i).name());
            }

            int r = 1;
            for (List<Object> row : result.rows()) {
                Row xr = sheet.createRow(r++);
                for (int c = 0; c < row.size(); c++) {
                    Object v = row.get(c);
                    if (v == null) {
                        continue;
                    }
                    Cell cell = xr.createCell(c);
                    if (v instanceof Number num) {
                        cell.setCellValue(num.doubleValue());
                    } else {
                        cell.setCellValue(String.valueOf(v));
                    }
                }
            }

            sheet.createFreezePane(0, 1);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("导出失败: " + e.getMessage(), e);
        }
    }
}