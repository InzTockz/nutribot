package com.battilana.nutribot.controller.admin;

import com.battilana.nutribot.dto.RangoFechas;
import com.battilana.nutribot.dto.RevisionRequest;
import com.battilana.nutribot.model.Interaccion;
import com.battilana.nutribot.service.InteraccionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * HU13: revision del administrador sobre las respuestas del bot (precision). Sprint 4: solo ADMIN.
 *  GET   /api/admin/interacciones/revision          respuestas aun sin revisar
 *  PATCH /api/admin/interacciones/{id}/revision     { "revision": "CORRECTA" | "INCORRECTA" }
 */
@RestController
@RequestMapping("/api/admin/interacciones")
@RequiredArgsConstructor
public class RevisionController {

    private final InteraccionService service;

    @GetMapping("/revision")
    public List<Interaccion> pendientesDeRevision(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.pendientesDeRevision(RangoFechas.de(desde, hasta));
    }

    @PatchMapping("/{id}/revision")
    public Interaccion revisar(@PathVariable Long id, @RequestBody RevisionRequest req) {
        return service.revisar(id, req.revision());
    }
}
