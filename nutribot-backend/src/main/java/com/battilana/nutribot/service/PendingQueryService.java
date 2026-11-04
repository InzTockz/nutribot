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

    /** DEF-11: longitud maxima de la consulta (columna pending_query.consulta). */
    static final int MAX_CONSULTA = 1000;

    private final PendingQueryRepository repository;
    private final ExcelExporter excel;

    /**
     * HU09: registra la consulta del cliente.
     * DEF-11: valida los datos antes de guardar (antes se guardaba cualquier texto como telefono,
     * y el asesor no podia devolver el contacto). Responde 400 con un mensaje claro si algo no es valido.
     */
    public PendingQuery registrar(PendingQueryRequest req) {
        String nombre = (req.nombre() == null) ? "" : req.nombre().trim();
        if (nombre.length() < 2 || nombre.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa tu nombre (entre 2 y 100 caracteres).");
        }
        String consulta = (req.consulta() == null) ? "" : req.consulta().trim();
        if (consulta.length() > MAX_CONSULTA) {
            consulta = consulta.substring(0, MAX_CONSULTA);   // evita que una consulta larga rompa el registro
        }
        return repository.save(PendingQuery.builder()
                .nombre(nombre)
                .telefono(normalizarCelular(req.telefono()))
                .consulta(consulta)
                .estado(PENDIENTE)
                .build());
    }

    /**
     * DEF-11: deja solo un celular peruano de 9 digitos que empieza con 9.
     * Acepta espacios, guiones, parentesis y el prefijo +51 o 51 al escribirlo ("+51 987 654 321" -> "987654321").
     * El asesor devuelve el contacto por WhatsApp, por eso no se aceptan telefonos fijos.
     */
    static String normalizarCelular(String telefono) {
        String digitos = (telefono == null) ? "" : telefono.replaceAll("[\\s\\-().]", "");
        if (digitos.startsWith("+51")) {
            digitos = digitos.substring(3);
        } else if (digitos.startsWith("51") && digitos.length() == 11) {
            digitos = digitos.substring(2);
        }
        if (!digitos.matches("9\\d{8}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ingresa un número de celular válido: 9 dígitos que empiecen con 9.");
        }
        return digitos;
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
