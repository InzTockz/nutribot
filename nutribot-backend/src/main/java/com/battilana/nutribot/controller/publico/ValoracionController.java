package com.battilana.nutribot.controller.publico;

import com.battilana.nutribot.dto.ValoracionRequest;
import com.battilana.nutribot.service.InteraccionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HU13: el cliente valora una respuesta del bot desde el chat (API publica, sin login).
 *  PATCH /api/public/interacciones/{id}/valoracion   { "valoracion": "UTIL" | "NO_UTIL" }
 * Devuelve 204 sin contenido: el cliente no necesita (ni debe) recibir los datos de la interaccion.
 */
@RestController
@RequestMapping("/api/public/interacciones")
@RequiredArgsConstructor
public class ValoracionController {

    private final InteraccionService service;

    @PatchMapping("/{id}/valoracion")
    public ResponseEntity<Void> valorar(@PathVariable Long id, @RequestBody ValoracionRequest req) {
        service.valorar(id, req.valoracion());
        return ResponseEntity.noContent().build();
    }
}
