package com.battilana.nutribot.controller.admin;

import com.battilana.nutribot.dto.RangoFechas;
import com.battilana.nutribot.model.Interaccion;
import com.battilana.nutribot.service.InteraccionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * HU07/HU08: consultas en las que el bot derivo u ofrecio al asesor (accion BOTON o PREGUNTAR),
 * para la pantalla "Clasificacion y derivacion". Sprint 4: ADMIN o ASESOR.
 *  GET /api/admin/derivaciones?desde=&hasta=
 */
@RestController
@RequestMapping("/api/admin/derivaciones")
@RequiredArgsConstructor
public class DerivacionController {

    private final InteraccionService service;

    @GetMapping
    public List<Interaccion> derivaciones(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.derivaciones(RangoFechas.de(desde, hasta));
    }
}
