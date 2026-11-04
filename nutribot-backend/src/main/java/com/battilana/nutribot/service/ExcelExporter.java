package com.battilana.nutribot.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sprint 4 (HU11, HU13): genera archivos Excel (.xlsx) con Apache POI.
 *
 * Uso:  excel.libro().hoja("Bitacora", encabezados, filas).bytes()
 * Cada hoja lleva el encabezado resaltado, la primera fila fija y columnas autoajustadas.
 */
@Component
public class ExcelExporter {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private static final int ANCHO_MAXIMO = 60 * 256;   // 60 caracteres: tope para consultas y respuestas largas

    public Libro libro() {
        return new Libro();
    }

    /** Respuesta HTTP de descarga: "prefijo-20261027-2215.xlsx". */
    public static ResponseEntity<byte[]> descarga(byte[] archivo, String prefijo) {
        String nombre = prefijo + "-" + LocalDateTime.now().format(SELLO) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(XLSX)
                .body(archivo);
    }

    /** Un libro de Excel en construccion. */
    public static class Libro {

        private final Workbook wb = new XSSFWorkbook();
        private final CellStyle encabezado;

        private Libro() {
            Font negrita = wb.createFont();
            negrita.setBold(true);
            negrita.setColor(IndexedColors.WHITE.getIndex());
            encabezado = wb.createCellStyle();
            encabezado.setFont(negrita);
            encabezado.setFillForegroundColor(IndexedColors.GREEN.getIndex());
            encabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        public Libro hoja(String nombre, List<String> encabezados, List<List<Object>> filas) {
            Sheet sheet = wb.createSheet(nombre);
            Row cabecera = sheet.createRow(0);
            for (int c = 0; c < encabezados.size(); c++) {
                Cell cell = cabecera.createCell(c);
                cell.setCellValue(encabezados.get(c));
                cell.setCellStyle(encabezado);
            }
            for (int r = 0; r < filas.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<Object> fila = filas.get(r);
                for (int c = 0; c < fila.size(); c++) {
                    escribir(row.createCell(c), fila.get(c));
                }
            }
            for (int c = 0; c < encabezados.size(); c++) {
                sheet.autoSizeColumn(c);
                if (sheet.getColumnWidth(c) > ANCHO_MAXIMO) {
                    sheet.setColumnWidth(c, ANCHO_MAXIMO);
                }
            }
            sheet.createFreezePane(0, 1);
            return this;
        }

        public byte[] bytes() {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                wb.write(out);
                wb.close();
                return out.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo generar el archivo Excel", e);
            }
        }

        private static void escribir(Cell cell, Object valor) {
            if (valor == null) {
                cell.setBlank();
            } else if (valor instanceof Number n) {
                cell.setCellValue(n.doubleValue());
            } else if (valor instanceof LocalDateTime t) {
                cell.setCellValue(t.format(FECHA_HORA));
            } else if (valor instanceof LocalDate d) {
                cell.setCellValue(d.format(FECHA));
            } else {
                cell.setCellValue(valor.toString());
            }
        }
    }
}
