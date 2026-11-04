package com.battilana.nutribot.repository;

import com.battilana.nutribot.model.Interaccion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface InteraccionRepository extends JpaRepository<Interaccion, Long> {

    /** HU12/HU13: interacciones de un intervalo [desde, hasta), de la mas reciente a la mas antigua. */
    List<Interaccion> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            LocalDateTime desde, LocalDateTime hasta);
}
