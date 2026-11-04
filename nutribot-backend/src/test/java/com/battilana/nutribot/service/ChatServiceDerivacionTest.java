package com.battilana.nutribot.service;

import com.battilana.nutribot.dto.ChatResponse;
import com.battilana.nutribot.model.CaminoConsulta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DEF-15: cuando el cliente pide un asesor, el mensaje del bot y la bandera asesorDisponible
 * dependen del horario de atencion (antes decia "boton de abajo" aunque no hubiera asesor).
 */
@DisplayName("ChatService — derivación según el horario de atención")
class ChatServiceDerivacionTest {

    private final HorarioAtencion horario = mock(HorarioAtencion.class);
    private final InteraccionService interacciones = mock(InteraccionService.class);
    private final ChatService service = new ChatService(
            mock(ChatClient.class), mock(ChatClient.class), mock(ChatClient.class),
            mock(VectorStore.class), interacciones, mock(ChatMemory.class), horario);

    @Test
    void enHorarioOfreceElBotonDeWhatsapp() {
        when(horario.disponible()).thenReturn(true);
        when(interacciones.registrar(any(), any(), any(), any(), any(), anyLong())).thenReturn(20L);

        ChatResponse r = service.responder("Quiero hablar con un asesor", "c1", "");

        assertThat(r.accion()).isEqualTo("BOTON");
        assertThat(r.asesorDisponible()).isTrue();
        assertThat(r.answer()).contains("botón de abajo");
        assertThat(r.interaccionId()).isEqualTo(20L);
    }

    @Test
    void fueraDeHorarioPideLosDatosYNoMencionaElBoton() {
        when(horario.disponible()).thenReturn(false);
        when(horario.descripcion()).thenReturn("de lunes a viernes de 8:00 a 18:00 y los sábados de 8:00 a 13:00");

        ChatResponse r = service.responder("Quiero hablar con un asesor", "c2", "");

        assertThat(r.accion()).isEqualTo("BOTON");
        assertThat(r.asesorDisponible()).isFalse();
        assertThat(r.answer()).doesNotContain("botón").contains("fuera del horario").contains("Déjanos tus datos");
        verify(interacciones).registrar(eq("c2"), any(), any(), eq(CaminoConsulta.ASESOR), eq("BOTON"), anyLong());
    }
}
