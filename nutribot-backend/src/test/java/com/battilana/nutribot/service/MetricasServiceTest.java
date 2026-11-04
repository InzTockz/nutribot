package com.battilana.nutribot.service;

import com.battilana.nutribot.dto.MetricasResponse;
import com.battilana.nutribot.dto.RangoFechas;
import com.battilana.nutribot.model.CaminoConsulta;
import com.battilana.nutribot.model.Interaccion;
import com.battilana.nutribot.model.Revision;
import com.battilana.nutribot.model.Valoracion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Sprint 4 (HU12, HU13): las formulas de los indicadores que se reportan en la tesis. */
@DisplayName("MetricasService — cálculo de indicadores")
class MetricasServiceTest {

    private final InteraccionService interacciones = mock(InteraccionService.class);
    private final MetricasService service = new MetricasService(interacciones, new ExcelExporter());
    private final LocalDate hoy = LocalDate.of(2026, 11, 4);
    private final RangoFechas rango = RangoFechas.de(hoy.minusDays(6), hoy);

    private Interaccion i(CaminoConsulta camino, String accion, Long ms, Valoracion v, Revision r) {
        return Interaccion.builder().pregunta("p").respuesta("r").camino(camino).accion(accion)
                .tiempoMs(ms).valoracion(v).revision(r).createdAt(hoy.atTime(10, 0)).build();
    }

    private List<Interaccion> datos() {
        return List.of(
                i(CaminoConsulta.GENERAL, "NINGUNA", 2000L, Valoracion.UTIL, Revision.CORRECTA),
                i(CaminoConsulta.PRECIO, "BOTON", 3000L, Valoracion.NO_UTIL, null),
                i(CaminoConsulta.ASESOR, "BOTON", 10L, null, null),
                i(CaminoConsulta.ERROR, "ERROR", null, null, null));
    }

    @Test
    void calculaLosIndicadoresConLasFormulasDeLaTesis() {
        when(interacciones.entre(rango)).thenReturn(datos());

        MetricasResponse m = service.calcular(rango);

        assertThat(m.totalConsultas()).isEqualTo(4);
        assertThat(m.tiempoPromedioSeg()).isEqualTo(1.67);       // (2000 + 3000 + 10) / 3, sin el error
        assertThat(m.derivaciones()).isEqualTo(2);
        assertThat(m.porcentajeDerivacion()).isEqualTo(50.0);     // 2 BOTON / 4
        assertThat(m.errores()).isEqualTo(1);
        assertThat(m.fiabilidad()).isEqualTo(75.0);               // 3 sin error / 4
        assertThat(m.satisfaccion()).isEqualTo(50.0);             // 1 UTIL / 2 valoradas
        assertThat(m.precision()).isEqualTo(100.0);               // 1 CORRECTA / 1 revisada
        assertThat(m.pendientesRevision()).isEqualTo(1);          // PRECIO sin revisar (ASESOR y ERROR no se revisan)
        assertThat(m.porCamino()).containsEntry("GENERAL", 1L).containsEntry("ERROR", 1L);
        assertThat(m.consultasMedidas()).isEqualTo(3);            // DEF-14: las 3 sin error tienen tiempo
        assertThat(m.bajoMeta()).isEqualTo(3);
        assertThat(m.porcentajeBajoMeta()).isEqualTo(100.0);
    }

    @Test
    void unArranqueEnFrioSubeElPromedioPeroElPorcentajeBajo5sLoDelata() {
        // DEF-14: caso real del recorrido — 12 consultas rapidas y 1 de 33.5 s (primera tras reiniciar)
        List<Interaccion> lista = new java.util.ArrayList<>();
        for (int k = 0; k < 12; k++) {
            lista.add(i(CaminoConsulta.GENERAL, "NINGUNA", 2500L, null, null));
        }
        lista.add(i(CaminoConsulta.GENERAL, "NINGUNA", 33_563L, null, null));
        when(interacciones.entre(rango)).thenReturn(lista);

        MetricasResponse m = service.calcular(rango);

        assertThat(m.tiempoPromedioSeg()).isGreaterThan(4.5);     // el promedio casi rompe la meta por un solo caso
        assertThat(m.consultasMedidas()).isEqualTo(13);
        assertThat(m.bajoMeta()).isEqualTo(12);
        assertThat(m.porcentajeBajoMeta()).isEqualTo(92.31);      // 12 / 13 — muestra que es un caso aislado
    }

    @Test
    void laSerieTieneUnPuntoPorCadaDiaDelRango() {
        when(interacciones.entre(rango)).thenReturn(datos());

        MetricasResponse m = service.calcular(rango);

        assertThat(m.serie()).hasSize(7);
        assertThat(m.serie().get(6).consultas()).isEqualTo(4);   // hoy
        assertThat(m.serie().get(0).consultas()).isZero();       // dias sin consultas valen 0
        assertThat(m.serie().get(0).tiempoPromedioSeg()).isNull();
    }

    @Test
    void sinDatosLosPorcentajesSonNulosYNoCero() {
        when(interacciones.entre(rango)).thenReturn(List.of());

        MetricasResponse m = service.calcular(rango);

        assertThat(m.totalConsultas()).isZero();
        assertThat(m.tiempoPromedioSeg()).isNull();
        assertThat(m.satisfaccion()).isNull();
        assertThat(m.precision()).isNull();
        assertThat(m.porcentajeBajoMeta()).isNull();
    }

    @Test
    void elReporteEsUnArchivoExcelValido() {
        when(interacciones.entre(rango)).thenReturn(datos());

        byte[] archivo = service.exportarReporte(rango);

        assertThat(archivo).isNotEmpty();
        assertThat(new String(archivo, 0, 2)).isEqualTo("PK");   // un .xlsx es un ZIP
    }
}
