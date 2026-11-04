package com.battilana.nutribot.controller;

import com.battilana.nutribot.dto.MetricasResponse;
import com.battilana.nutribot.dto.RangoFechas;
import com.battilana.nutribot.service.ExcelExporter;
import com.battilana.nutribot.service.MetricasService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Panel de metricas.
 *  GET /api/metrics?desde=&hasta=          indicadores y serie diaria (HU12, HU13)
 *  GET /api/metrics/export?desde=&hasta=   reporte en Excel (HU13)
 * Sin fechas se usan los ultimos 30 dias.
 */
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricasController {

    private final MetricasService service;

    @GetMapping
    public MetricasResponse metricas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.calcular(RangoFechas.de(desde, hasta));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ExcelExporter.descarga(service.exportarReporte(RangoFechas.de(desde, hasta)), "reporte-nutribot");
    }
}
