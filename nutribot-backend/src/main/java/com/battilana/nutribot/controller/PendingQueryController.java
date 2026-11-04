package com.battilana.nutribot.controller;

import com.battilana.nutribot.dto.EstadoRequest;
import com.battilana.nutribot.dto.PendingQueryRequest;
import com.battilana.nutribot.model.PendingQuery;
import com.battilana.nutribot.service.ExcelExporter;
import com.battilana.nutribot.service.PendingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Bitacora de consultas pendientes.
 *  POST  /api/pending-queries                 registra una consulta (HU09)
 *  GET   /api/pending-queries                 la lista (HU10)
 *  PATCH /api/pending-queries/{id}/estado     marca atendida / reabre (HU11)
 *  GET   /api/pending-queries/export          descarga la bitacora filtrada en Excel (HU11)
 */
@RestController
@RequestMapping("/api/pending-queries")
@RequiredArgsConstructor
public class PendingQueryController {

    private final PendingQueryService service;

    @PostMapping
    public PendingQuery create(@RequestBody PendingQueryRequest req) {
        return service.registrar(req);
    }

    @GetMapping
    public List<PendingQuery> list() {
        return service.listar();
    }

    @PatchMapping("/{id}/estado")
    public PendingQuery cambiarEstado(@PathVariable Long id, @RequestBody EstadoRequest req) {
        return service.cambiarEstado(id, req.estado());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ExcelExporter.descarga(service.exportarExcel(estado, fecha), "bitacora");
    }
}
