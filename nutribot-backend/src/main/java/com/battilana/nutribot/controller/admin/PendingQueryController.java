package com.battilana.nutribot.controller.admin;

import com.battilana.nutribot.dto.EstadoRequest;
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
 * Bitacora de consultas pendientes, vista del personal (Sprint 4: ADMIN o ASESOR).
 *  GET   /api/admin/pending-queries                 lista (HU10)
 *  PATCH /api/admin/pending-queries/{id}/estado     marca atendida / reabre (HU11)
 *  GET   /api/admin/pending-queries/export          bitacora filtrada en Excel (HU11)
 * El registro de una consulta por el cliente (HU09) esta en la API publica: RegistroConsultaController.
 */
@RestController
@RequestMapping("/api/admin/pending-queries")
@RequiredArgsConstructor
public class PendingQueryController {

    private final PendingQueryService service;

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
