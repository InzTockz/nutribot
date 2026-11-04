package com.battilana.nutribot.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Sprint 4: indicadores del panel de metricas.
 *  - HU12: volumen, tiempo de respuesta, derivaciones (y fiabilidad) con su serie diaria.
 *  - HU13: satisfaccion (valoracion del cliente) y precision (revision del administrador).
 * Los porcentajes y promedios son null cuando no hay datos para calcularlos.
 */
public record MetricasResponse(
        LocalDate desde,
        LocalDate hasta,
        long totalConsultas,
        Double tiempoPromedioSeg,
        long consultasMedidas,          // DEF-14: consultas sin error con tiempo registrado
        long bajoMeta,                  // DEF-14: de ellas, las respondidas en menos de 5 s
        Double porcentajeBajoMeta,      // DEF-14: escenario de calidad "< 5 s en el 95 % de los casos"
        long derivaciones,
        Double porcentajeDerivacion,
        long errores,
        Double fiabilidad,
        Map<String, Long> porCamino,
        long valoradas,
        long utiles,
        Double satisfaccion,
        long revisadas,
        long correctas,
        Double precision,
        long pendientesRevision,
        List<MetricaDia> serie) {

    /** Un punto del grafico de tendencia. */
    public record MetricaDia(LocalDate fecha, long consultas, Double tiempoPromedioSeg, long derivaciones) {
    }
}
