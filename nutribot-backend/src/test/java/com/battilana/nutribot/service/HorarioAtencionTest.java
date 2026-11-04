package com.battilana.nutribot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** DEF-15: el horario de atencion se decide en el backend, siempre en hora de Peru. */
@DisplayName("HorarioAtencion — disponibilidad de asesores")
class HorarioAtencionTest {

    private final HorarioAtencion horario = new HorarioAtencion("America/Lima", "08:00-18:00", "08:00-13:00", Clock.systemUTC());

    /** Fecha y hora de Lima (UTC-5) */
    private static ZonedDateTime lima(int mes, int dia, int hora, int minuto) {
        return ZonedDateTime.of(2026, mes, dia, hora, minuto, 0, 0, java.time.ZoneId.of("America/Lima"));
    }

    @Test
    void deLunesAViernesAtiendeDe8a18SinIncluirLas18() {
        // miercoles 04/11/2026
        assertThat(horario.disponibleEn(lima(11, 4, 7, 59))).isFalse();
        assertThat(horario.disponibleEn(lima(11, 4, 8, 0))).isTrue();
        assertThat(horario.disponibleEn(lima(11, 4, 17, 59))).isTrue();
        assertThat(horario.disponibleEn(lima(11, 4, 18, 0))).isFalse();
    }

    @Test
    void elSabadoAtiendeDe8a13YElDomingoNoAtiende() {
        assertThat(horario.disponibleEn(lima(11, 7, 12, 59))).isTrue();    // sabado
        assertThat(horario.disponibleEn(lima(11, 7, 13, 0))).isFalse();
        assertThat(horario.disponibleEn(lima(11, 8, 10, 0))).isFalse();    // domingo
    }

    @Test
    void usaLaHoraDePeruAunqueElServidorEsteEnOtraZona() {
        // 15:00 UTC = 10:00 en Lima (abierto); 23:30 UTC = 18:30 en Lima (cerrado)
        assertThat(horario.disponibleEn(ZonedDateTime.of(2026, 11, 4, 15, 0, 0, 0, ZoneOffset.UTC))).isTrue();
        assertThat(horario.disponibleEn(ZonedDateTime.of(2026, 11, 4, 23, 30, 0, 0, ZoneOffset.UTC))).isFalse();
    }

    @Test
    void disponibleUsaElRelojInyectado() {
        Clock domingo = Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneOffset.UTC);
        Clock miercoles = Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneOffset.UTC);
        assertThat(new HorarioAtencion("America/Lima", "08:00-18:00", "08:00-13:00", domingo).disponible()).isFalse();
        assertThat(new HorarioAtencion("America/Lima", "08:00-18:00", "08:00-13:00", miercoles).disponible()).isTrue();
    }

    @Test
    void describeElHorarioParaElClienteYRechazaFormatosInvalidos() {
        assertThat(horario.descripcion()).isEqualTo("de lunes a viernes de 8:00 a 18:00 y los sábados de 8:00 a 13:00");
        assertThatThrownBy(() -> new HorarioAtencion("America/Lima", "18:00-08:00", "08:00-13:00", Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HorarioAtencion("America/Lima", "8 a 18", "08:00-13:00", Clock.systemUTC()))
                .isInstanceOf(Exception.class);
    }
}
