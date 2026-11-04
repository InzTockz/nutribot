package com.battilana.nutribot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * DEF-14: precalienta los modelos de Ollama al arrancar el backend.
 *
 * Ollama carga Mistral en la GPU recien con la primera consulta; esa primera respuesta tardo 33.5 s
 * en el recorrido del Sprint 4. Al terminar de arrancar, el backend hace en segundo plano una consulta
 * interna al modelo de chat y al de embeddings, para que el primer cliente ya lo encuentre cargado.
 * Esta consulta no pasa por ChatService, por lo que NO se registra en la tabla interaccion ni altera las metricas.
 * Junto con spring.ai.ollama.*.options.keep-alive (8 h), evita los arranques en frio durante la jornada.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PrecalentamientoModelo {

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;

    @Value("${nutribot.ollama.precalentar:true}")
    private boolean activo;

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        if (activo) {
            Thread.ofVirtual().name("precalentamiento-ollama").start(this::precalentar);
        }
    }

    void precalentar() {
        long inicio = System.nanoTime();
        try {
            embeddingModel.embed("precalentamiento");
            chatModel.call("Responde solo con la palabra OK.");
            log.info("Modelo precalentado en {} s: las consultas de los clientes ya no esperan la carga de Mistral.",
                    String.format("%.1f", (System.nanoTime() - inicio) / 1e9));
        } catch (Exception e) {
            log.warn("No se pudo precalentar el modelo (¿Ollama esta encendido?): {}", e.getMessage());
        }
    }
}
