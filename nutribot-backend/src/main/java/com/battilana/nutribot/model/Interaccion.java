package com.battilana.nutribot.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Sprint 4 (enabler de HU12 y HU13): registro de cada consulta atendida por el chat.
 *
 * Es el "cuaderno" del que salen las metricas del panel (volumen, tiempo de respuesta,
 * derivaciones) y donde se guardan la valoracion del cliente (satisfaccion) y la revision
 * del administrador (precision).
 *
 * Derivacion: se considera derivada toda interaccion con accion = BOTON (el bot ofrecio
 * directamente al asesor). PREGUNTAR es una oferta que el cliente aun debe confirmar.
 *
 * Los enums se guardan como VARCHAR (@JdbcTypeCode): Hibernate 6 en MySQL los crearia como
 * columnas ENUM, y ddl-auto=update no las altera si mas adelante se agrega un valor nuevo.
 */
@Entity
@Table(name = "interaccion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Interaccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64)
    private String conversationId;      // agrupa las preguntas de una misma sesion (HU05)

    @Column(nullable = false, columnDefinition = "TEXT")
    private String pregunta;

    @Column(columnDefinition = "TEXT")
    private String respuesta;           // null cuando camino = ERROR

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private CaminoConsulta camino;

    @Column(nullable = false, length = 20)
    private String accion;              // NINGUNA / BOTON / PREGUNTAR / ERROR

    private Long tiempoMs;              // tiempo total de atencion, en milisegundos

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 10)
    private Valoracion valoracion;      // HU13: null hasta que el cliente valore

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 12)
    private Revision revision;          // HU13: null hasta que el administrador revise

    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
