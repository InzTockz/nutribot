package com.battilana.nutribot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Sprint 4 (deuda tecnica, riesgo R14): las conversaciones inactivas liberan su memoria. */
@DisplayName("ChatService — liberación de conversaciones inactivas")
class ChatServiceMemoriaTest {

    private final ChatMemory memoria = mock(ChatMemory.class);
    private final ChatService service = new ChatService(
            mock(ChatClient.class), mock(ChatClient.class), mock(ChatClient.class),
            mock(VectorStore.class), mock(InteraccionService.class), memoria, mock(HorarioAtencion.class));

    @Test
    void liberaSoloLasConversacionesInactivas() {
        Instant ahora = Instant.parse("2026-11-04T15:00:00Z");
        service.registrarActividad("vieja", ahora.minus(Duration.ofHours(3)));
        service.registrarActividad("reciente", ahora.minus(Duration.ofMinutes(10)));

        int liberadas = service.liberarInactivas(ahora, Duration.ofMinutes(120));

        assertThat(liberadas).isEqualTo(1);
        assertThat(service.conversacionesActivas()).isEqualTo(1);
        verify(memoria).clear("vieja");
        verify(memoria, never()).clear("reciente");
    }

    @Test
    void sinConversacionesNoHaceNada() {
        assertThat(service.liberarInactivas(Instant.now(), Duration.ofMinutes(120))).isZero();
        verifyNoInteractions(memoria);
    }
}
