package com.battilana.nutribot.dto;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Sprint 4 (HU12, HU13): rango de fechas de consulta, con ambos extremos incluidos.
 * Sin fechas, se toman los ultimos 30 dias.
 */
public record RangoFechas(LocalDate desde, LocalDate hasta) {

    public static final int MAX_DIAS = 366;

    public static RangoFechas de(LocalDate desde, LocalDate hasta) {
        LocalDate h = (hasta != null) ? hasta : LocalDate.now();
        LocalDate d = (desde != null) ? desde : h.minusDays(29);
        if (d.isAfter(h)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
        if (ChronoUnit.DAYS.between(d, h) >= MAX_DIAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El rango maximo es de " + MAX_DIAS + " dias.");
        }
        return new RangoFechas(d, h);
    }
}
