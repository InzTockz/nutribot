package com.battilana.nutribot.service;

import com.battilana.nutribot.dto.RangoFechas;
import com.battilana.nutribot.model.CaminoConsulta;
import com.battilana.nutribot.model.Interaccion;
import com.battilana.nutribot.model.Revision;
import com.battilana.nutribot.model.Valoracion;
import com.battilana.nutribot.repository.InteraccionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Sprint 4: interacciones del chat.
 *  - Enabler: registrar cada consulta (sin tumbar el chat si MySQL falla).
 *  - HU13: valoracion del cliente (satisfaccion) y revision del administrador (precision).
 *  - HU12/HU13: consulta por rango de fechas para el panel de metricas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InteraccionService {

    private final InteraccionRepository repository;

    /**
     * Guarda la interaccion y devuelve su id (el frontend lo usa para valorarla en HU13).
     * Devuelve null si no se pudo guardar: el cliente igual recibe su respuesta.
     */
    public Long registrar(String conversationId, String pregunta, String respuesta,
                          CaminoConsulta camino, String accion, long tiempoMs) {
        try {
            Interaccion guardada = repository.save(Interaccion.builder()
                    .conversationId(conversationId)
                    .pregunta(pregunta)
                    .respuesta(respuesta)
                    .camino(camino)
                    .accion(accion)
                    .tiempoMs(tiempoMs)
                    .build());
            return guardada.getId();
        } catch (Exception e) {
            log.warn("No se pudo registrar la interaccion ({}): {}", camino, e.getMessage());
            return null;
        }
    }

    /** Interacciones del rango (ambos dias incluidos), de la mas reciente a la mas antigua. */
    public List<Interaccion> entre(RangoFechas rango) {
        return repository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                rango.desde().atStartOfDay(), rango.hasta().plusDays(1).atStartOfDay());
    }

    /** HU13: el cliente marca la respuesta como util o no util. */
    @Transactional
    public Interaccion valorar(Long id, String valor) {
        Interaccion interaccion = buscar(id);
        interaccion.setValoracion(convertir(Valoracion.class, valor, "Valoracion no valida. Use UTIL o NO_UTIL."));
        return repository.save(interaccion);
    }

    /** HU13: el administrador marca la respuesta como correcta o incorrecta (precision). */
    @Transactional
    public Interaccion revisar(Long id, String valor) {
        Interaccion interaccion = buscar(id);
        if (!esRevisable(interaccion)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Esta interaccion no se revisa (derivacion directa o error tecnico).");
        }
        interaccion.setRevision(convertir(Revision.class, valor, "Revision no valida. Use CORRECTA o INCORRECTA."));
        return repository.save(interaccion);
    }

    /** HU13: respuestas del rango que el administrador aun no ha revisado. */
    public List<Interaccion> pendientesDeRevision(RangoFechas rango) {
        return entre(rango).stream()
                .filter(i -> i.getRevision() == null && esRevisable(i))
                .toList();
    }

    /**
     * Solo se revisan las respuestas redactadas por el bot. Se excluyen la derivacion pedida por el
     * cliente (texto fijo, no hay nada que evaluar) y los errores tecnicos (no hubo respuesta).
     */
    public static boolean esRevisable(Interaccion i) {
        return i.getCamino() != CaminoConsulta.ASESOR && i.getCamino() != CaminoConsulta.ERROR;
    }

    private Interaccion buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interaccion no encontrada."));
    }

    private static <E extends Enum<E>> E convertir(Class<E> tipo, String valor, String mensajeError) {
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensajeError);
        }
    }
}
