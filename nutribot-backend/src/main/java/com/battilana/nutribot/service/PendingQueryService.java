package com.battilana.nutribot.service;

import com.battilana.nutribot.dto.PendingQueryRequest;
import com.battilana.nutribot.model.PendingQuery;
import com.battilana.nutribot.repository.PendingQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Bitacora de consultas pendientes.
 *  - HU09: registrar la consulta cuando no hay asesor.
 *  - HU10: listarla (los filtros de la vista se aplican en el frontend).
 *  - HU11: cambiar su estado y exportarla a Excel con los mismos filtros de la vista.
 */
@Service
@RequiredArgsConstructor
public class PendingQueryService {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ATENDIDA = "ATENDIDA";

    private final PendingQueryRepository repository;
    private final ExcelExporter excel;

    /** HU09 */
    public PendingQuery registrar(PendingQueryRequest req) {
        return repository.save(PendingQuery.builder()
                .nombre(req.nombre())
                .telefono(req.telefono())
                .consulta(req.consulta())
                .estado(PENDIENTE)
                .build());
    }

    /** HU10: de la mas reciente a la mas antigua. */
    public List<PendingQuery> listar() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /** HU11: PENDIENTE -> ATENDIDA (guarda la fecha de atencion) o ATENDIDA -> PENDIENTE (reabrir). */
    @Transactional
    public PendingQuery cambiarEstado(Long id, String estado) {
        String nuevo = (estado == null) ? "" : estado.trim().toUpperCase();
        if (!PENDIENTE.equals(nuevo) && !ATENDIDA.equals(nuevo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado no valido. Use PENDIENTE o ATENDIDA.");
        }
        PendingQuery consulta = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consulta no encontrada."));
        consulta.setEstado(nuevo);
        consulta.setAtendidaAt(ATENDIDA.equals(nuevo) ? LocalDateTime.now() : null);
        return repository.save(consulta);
    }

    /** Mismos filtros que la vista: estado (TODOS / PENDIENTE / ATENDIDA) y dia de registro. */
    public List<PendingQuery> filtrar(String estado, LocalDate fecha) {
        boolean todos = (estado == null || estado.isBlank() || "TODOS".equalsIgnoreCase(estado));
        return listar().stream()
                .filter(q -> todos || estado.equalsIgnoreCase(q.getEstado()))
                .filter(q -> fecha == null
                        || (q.getCreatedAt() != null && q.getCreatedAt().toLocalDate().equals(fecha)))
                .toList();
    }

    /** HU11: la bitacora filtrada, en Excel. */
    public byte[] exportarExcel(String estado, LocalDate fecha) {
        List<List<Object>> filas = filtrar(estado, fecha).stream()
                .map(q -> Arrays.<Object>asList(
                        q.getId(), q.getNombre(), q.getTelefono(), q.getConsulta(),
                        q.getCreatedAt(), q.getEstado(), q.getAtendidaAt()))
                .toList();
        return excel.libro()
                .hoja("Bitacora",
                        List.of("N.°", "Cliente", "Numero", "Consulta", "Fecha / hora", "Estado", "Atendida el"),
                        filas)
                .bytes();
    }
}
