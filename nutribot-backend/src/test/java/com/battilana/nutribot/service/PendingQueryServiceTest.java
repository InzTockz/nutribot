package com.battilana.nutribot.service;

import com.battilana.nutribot.dto.PendingQueryRequest;
import com.battilana.nutribot.model.PendingQuery;
import com.battilana.nutribot.repository.PendingQueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Sprint 4 (HU09, HU11): registro validado (DEF-11), cambio de estado y filtros de la bitacora. */
@DisplayName("PendingQueryService — bitácora")
class PendingQueryServiceTest {

    private final PendingQueryRepository repo = mock(PendingQueryRepository.class);
    private final PendingQueryService service = new PendingQueryService(repo, new ExcelExporter());

    private PendingQuery consulta(Long id, String estado, LocalDateTime creada) {
        return PendingQuery.builder().id(id).nombre("Juan").telefono("987654321").consulta("Precio")
                .estado(estado).createdAt(creada).build();
    }

    @Test
    void marcarAtendidaGuardaLaFechaYReabrirLaBorra() {
        PendingQuery q = consulta(1L, "PENDIENTE", LocalDateTime.now());
        when(repo.findById(1L)).thenReturn(Optional.of(q));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PendingQuery atendida = service.cambiarEstado(1L, "atendida");
        assertThat(atendida.getEstado()).isEqualTo("ATENDIDA");
        assertThat(atendida.getAtendidaAt()).isNotNull();

        PendingQuery reabierta = service.cambiarEstado(1L, "PENDIENTE");
        assertThat(reabierta.getEstado()).isEqualTo("PENDIENTE");
        assertThat(reabierta.getAtendidaAt()).isNull();
    }

    @Test
    void unEstadoInventadoSeRechazaConError400() {
        assertThatThrownBy(() -> service.cambiarEstado(1L, "CERRADA"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        verify(repo, never()).save(any());
    }

    @Test
    void unaConsultaInexistenteDevuelve404() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cambiarEstado(99L, "ATENDIDA"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }

    @Test
    void filtraPorEstadoYPorDiaDeRegistro() {
        LocalDateTime dia1 = LocalDateTime.of(2026, 11, 1, 9, 0);
        LocalDateTime dia2 = LocalDateTime.of(2026, 11, 2, 9, 0);
        when(repo.findAll(any(Sort.class))).thenReturn(List.of(
                consulta(1L, "PENDIENTE", dia1), consulta(2L, "ATENDIDA", dia1), consulta(3L, "PENDIENTE", dia2)));

        assertThat(service.filtrar("PENDIENTE", null)).extracting(PendingQuery::getId).containsExactly(1L, 3L);
        assertThat(service.filtrar("TODOS", LocalDate.of(2026, 11, 1))).extracting(PendingQuery::getId).containsExactly(1L, 2L);
        assertThat(service.filtrar(null, null)).hasSize(3);
    }

    @Test
    void laExportacionEsUnArchivoExcelValido() {
        when(repo.findAll(any(Sort.class))).thenReturn(List.of(consulta(1L, "PENDIENTE", LocalDateTime.now())));
        byte[] archivo = service.exportarExcel("TODOS", null);
        assertThat(new String(archivo, 0, 2)).isEqualTo("PK");
    }

    // ---------- DEF-11: validacion del registro de la bitacora ----------

    @Test
    void aceptaCelularesPeruanosYLosGuardaLimpios() {
        assertThat(PendingQueryService.normalizarCelular("987654321")).isEqualTo("987654321");
        assertThat(PendingQueryService.normalizarCelular("+51 987 654 321")).isEqualTo("987654321");
        assertThat(PendingQueryService.normalizarCelular("51987654321")).isEqualTo("987654321");
        assertThat(PendingQueryService.normalizarCelular("987-654-321")).isEqualTo("987654321");
    }

    @Test
    void rechazaTextoFijosYLongitudesIncorrectasConError400() {
        for (String malo : new String[] {"Gonzales", "014567890", "98765432", "9876543210", "", null}) {
            assertThatThrownBy(() -> PendingQueryService.normalizarCelular(malo))
                    .as("telefono: %s", malo)
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        }
    }

    @Test
    void registrarValidaElNombreYNoGuardaSiEsInvalido() {
        assertThatThrownBy(() -> service.registrar(new PendingQueryRequest(" J ", "987654321", "Precio")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        assertThatThrownBy(() -> service.registrar(new PendingQueryRequest("Juan", "Gonzales", "Precio")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        verify(repo, never()).save(any());
    }

    @Test
    void registrarGuardaElCelularNormalizadoYRecortaConsultasLargas() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PendingQuery q = service.registrar(new PendingQueryRequest("  Juan Pérez ", "+51 987 654 321", "x".repeat(1500)));

        assertThat(q.getNombre()).isEqualTo("Juan Pérez");
        assertThat(q.getTelefono()).isEqualTo("987654321");
        assertThat(q.getConsulta()).hasSize(PendingQueryService.MAX_CONSULTA);
        assertThat(q.getEstado()).isEqualTo("PENDIENTE");
    }
}
