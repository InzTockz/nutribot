package com.battilana.nutribot.controller.publico;

import com.battilana.nutribot.dto.PendingQueryRequest;
import com.battilana.nutribot.service.PendingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * HU09: el cliente deja su nombre, numero y consulta cuando no hay un asesor disponible.
 *  POST /api/public/pending-queries   (API publica, sin login)
 * Solo confirma el registro: el cliente NO recibe datos de la bitacora (Ley N.° 29733).
 */
@RestController
@RequestMapping("/api/public/pending-queries")
@RequiredArgsConstructor
public class RegistroConsultaController {

    private final PendingQueryService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> registrar(@RequestBody PendingQueryRequest req) {
        return Map.of("id", service.registrar(req).getId(), "estado", "REGISTRADA");
    }
}
