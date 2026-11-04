package com.battilana.nutribot.controller;

import com.battilana.nutribot.dto.RangoFechas;
import com.battilana.nutribot.dto.RevisionRequest;
import com.battilana.nutribot.dto.ValoracionRequest;
import com.battilana.nutribot.model.Interaccion;
import com.battilana.nutribot.service.InteraccionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * HU13: valoracion y revision de las respuestas del bot.
 *  PATCH /api/interacciones/{id}/valoracion   el cliente: UTIL / NO_UTIL (satisfaccion)
 *  GET   /api/interacciones/revision          el administrador: respuestas aun sin revisar
 *  PATCH /api/interacciones/{id}/revision     el administrador: CORRECTA / INCORRECTA (precision)
 */
@RestController
@RequestMapping("/api/interacciones")
@RequiredArgsConstructor
public class InteraccionController {

    private final InteraccionService service;

    @PatchMapping("/{id}/valoracion")
    public Interaccion valorar(@PathVariable Long id, @RequestBody ValoracionRequest req) {
        return service.valorar(id, req.valoracion());
    }

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
