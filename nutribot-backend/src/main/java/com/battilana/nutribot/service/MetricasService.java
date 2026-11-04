package com.battilana.nutribot.service;

import com.battilana.nutribot.dto.MetricasResponse;
import com.battilana.nutribot.dto.MetricasResponse.MetricaDia;
import com.battilana.nutribot.dto.RangoFechas;
import com.battilana.nutribot.model.CaminoConsulta;
import com.battilana.nutribot.model.Interaccion;
import com.battilana.nutribot.model.Revision;
import com.battilana.nutribot.model.Valoracion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Sprint 4: calcula los indicadores del panel (HU12, HU13) a partir de la tabla interaccion
 * y genera el reporte en Excel (HU13).
 *
 * Definiciones (las mismas que se reportan en la tesis):
 *  - Tiempo promedio = promedio de tiempo_ms de las interacciones sin error, en segundos.
 *  - Derivacion      = interaccion con accion BOTON (el bot ofrecio directamente al asesor).
 *  - Fiabilidad      = interacciones sin error / total.
 *  - Satisfaccion    = valoradas UTIL / valoradas.
 *  - Precision       = revisadas CORRECTA / revisadas.
 */
@Service
@RequiredArgsConstructor
public class MetricasService {

    private final InteraccionService interacciones;
    private final ExcelExporter excel;

    public MetricasResponse calcular(RangoFechas rango) {
        return calcular(rango, interacciones.entre(rango));
    }

    private MetricasResponse calcular(RangoFechas rango, List<Interaccion> lista) {
        long total = lista.size();
        long errores = contar(lista, i -> i.getCamino() == CaminoConsulta.ERROR);
        long derivaciones = contar(lista, MetricasService::esDerivacion);
        long valoradas = contar(lista, i -> i.getValoracion() != null);
        long utiles = contar(lista, i -> i.getValoracion() == Valoracion.UTIL);
        long revisadas = contar(lista, i -> i.getRevision() != null);
        long correctas = contar(lista, i -> i.getRevision() == Revision.CORRECTA);
        long pendientesRevision = contar(lista, i -> i.getRevision() == null && InteraccionService.esRevisable(i));

        Map<String, Long> porCamino = lista.stream()
                .collect(Collectors.groupingBy(i -> i.getCamino().name(), TreeMap::new, Collectors.counting()));

        // Serie diaria: un punto por CADA dia del rango (los dias sin consultas valen 0)
        Map<LocalDate, List<Interaccion>> porDia = lista.stream()
                .collect(Collectors.groupingBy(i -> i.getCreatedAt().toLocalDate()));
        List<MetricaDia> serie = rango.desde().datesUntil(rango.hasta().plusDays(1))
                .map(dia -> {
                    List<Interaccion> delDia = porDia.getOrDefault(dia, List.of());
                    return new MetricaDia(dia, delDia.size(), tiempoPromedioSeg(delDia),
                            contar(delDia, MetricasService::esDerivacion));
                })
                .toList();

        return new MetricasResponse(
                rango.desde(), rango.hasta(),
                total, tiempoPromedioSeg(lista),
                derivaciones, porcentaje(derivaciones, total),
                errores, porcentaje(total - errores, total),
                porCamino,
                valoradas, utiles, porcentaje(utiles, valoradas),
                revisadas, correctas, porcentaje(correctas, revisadas),
                pendientesRevision,
                serie);
    }

    /** HU13: reporte en Excel con los indicadores, la serie diaria y el detalle de interacciones. */
    public byte[] exportarReporte(RangoFechas rango) {
        List<Interaccion> lista = interacciones.entre(rango);
        MetricasResponse m = calcular(rango, lista);

        List<List<Object>> indicadores = List.of(
                Arrays.<Object>asList("Periodo", m.desde() + " a " + m.hasta(), ""),
                Arrays.<Object>asList("Consultas", m.totalConsultas(), ""),
                Arrays.<Object>asList("Tiempo promedio de respuesta (s)", m.tiempoPromedioSeg(), "Meta: menos de 5 s"),
                Arrays.<Object>asList("Derivaciones (%)", m.porcentajeDerivacion(), m.derivaciones() + " de " + m.totalConsultas()),
                Arrays.<Object>asList("Fiabilidad (%)", m.fiabilidad(), m.errores() + " con error"),
                Arrays.<Object>asList("Satisfaccion (%)", m.satisfaccion(), m.utiles() + " utiles de " + m.valoradas() + " valoradas"),
                Arrays.<Object>asList("Precision (%)", m.precision(), m.correctas() + " correctas de " + m.revisadas() + " revisadas"),
                Arrays.<Object>asList("Pendientes de revision", m.pendientesRevision(), ""));

        List<List<Object>> porDia = m.serie().stream()
                .map(d -> Arrays.<Object>asList(d.fecha(), d.consultas(), d.tiempoPromedioSeg(), d.derivaciones()))
                .toList();

        List<List<Object>> detalle = lista.stream()
                .map(i -> Arrays.<Object>asList(
                        i.getId(), i.getCreatedAt(), i.getPregunta(), i.getRespuesta(),
                        i.getCamino(), i.getAccion(),
                        i.getTiempoMs() == null ? null : redondear(i.getTiempoMs() / 1000.0),
                        i.getValoracion(), i.getRevision()))
                .toList();

        return excel.libro()
                .hoja("Indicadores", List.of("Indicador", "Valor", "Detalle"), indicadores)
                .hoja("Por dia", List.of("Fecha", "Consultas", "Tiempo promedio (s)", "Derivaciones"), porDia)
                .hoja("Interacciones",
                        List.of("ID", "Fecha / hora", "Pregunta", "Respuesta", "Camino", "Accion",
                                "Tiempo (s)", "Valoracion", "Revision"),
                        detalle)
                .bytes();
    }

    private static boolean esDerivacion(Interaccion i) {
        return "BOTON".equals(i.getAccion());
    }

    private static long contar(List<Interaccion> lista, java.util.function.Predicate<Interaccion> condicion) {
        return lista.stream().filter(condicion).count();
    }

    private static Double tiempoPromedioSeg(List<Interaccion> lista) {
        OptionalDouble promedio = lista.stream()
                .filter(i -> i.getCamino() != CaminoConsulta.ERROR && i.getTiempoMs() != null)
                .mapToLong(Interaccion::getTiempoMs)
                .average();
        return promedio.isPresent() ? redondear(promedio.getAsDouble() / 1000.0) : null;
    }

    private static Double porcentaje(long parte, long total) {
        return total == 0 ? null : redondear(parte * 100.0 / total);
    }

    private static double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
