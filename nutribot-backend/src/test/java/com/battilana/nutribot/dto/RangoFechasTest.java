package com.battilana.nutribot.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sprint 4 (HU12): filtro de fechas del panel de metricas. */
@DisplayName("RangoFechas — filtro por fecha")
class RangoFechasTest {

    @Test
    void sinFechasTomaLosUltimos30Dias() {
        RangoFechas r = RangoFechas.de(null, null);
        assertThat(r.hasta()).isEqualTo(LocalDate.now());
        assertThat(r.desde()).isEqualTo(LocalDate.now().minusDays(29));
    }

    @Test
    void unSoloDiaEsValido() {
        LocalDate d = LocalDate.of(2026, 11, 4);
        assertThat(RangoFechas.de(d, d).desde()).isEqualTo(d);
    }

    @Test
    void desdePosteriorAHastaSeRechaza() {
        assertThatThrownBy(() -> RangoFechas.de(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 1)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
    }

    @Test
    void unRangoMayorA366DiasSeRechaza() {
        assertThatThrownBy(() -> RangoFechas.de(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 11, 4)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
