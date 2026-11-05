package com.battilana.nutribot.service;

import com.battilana.nutribot.dto.ChatResponse;
import com.battilana.nutribot.model.CaminoConsulta;
import com.battilana.nutribot.model.Interaccion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DEF-17: Battilana no tiene tienda ni punto de venta; todo se vende por WhatsApp. Ante la direccion,
 * la tienda o donde comprar, el bot da el mensaje oficial (con la planta central) sin pasar por el
 * modelo, que con el dato delante lo ignoraba o inventaba "sucursales" y una "pagina web".
 */
@DisplayName("ChatService — dirección, tienda y dónde comprar (DEF-17)")
class ChatServiceUbicacionTest {

    private final ChatClient chat = mock(ChatClient.class);
    private final ChatClient contexto = mock(ChatClient.class);
    private final VectorStore vectores = mock(VectorStore.class);
    private final InteraccionService interacciones = mock(InteraccionService.class);
    private final HorarioAtencion horario = mock(HorarioAtencion.class);
    private final ChatService service = new ChatService(
            chat, mock(ChatClient.class), contexto, vectores, interacciones, mock(ChatMemory.class), horario);

    @Test
    void lasPreguntasDeDireccionTiendaYDondeComprarRecibenElMensajeOficial() {
        when(horario.disponible()).thenReturn(true);
        for (String p : List.of("¿Cuál es su dirección?", "¿Tienen tienda o punto de venta?",
                "¿Dónde puedo comprar sus productos?", "¿Dónde están ubicados?", "¿Tienen sucursales?")) {
            ChatResponse r = service.responder(p, "u-" + p.length(), "");
            assertThat(r.accion()).as(p).isEqualTo("BOTON");
            assertThat(r.answer()).as(p).contains("no cuenta con tienda ni punto de venta")
                    .contains("WhatsApp").contains("Lurín").contains("botón de abajo")
                    .doesNotContain("sucursales locales").doesNotContain("página web");
        }
        verify(interacciones, times(5)).registrar(any(), any(), any(), eq(CaminoConsulta.UBICACION), eq("BOTON"), anyLong());
        verifyNoInteractions(chat, contexto, vectores);   // sin modelo y sin busqueda
    }

    @Test
    void fueraDeHorarioCierraConElFormulario() {
        when(horario.disponible()).thenReturn(false);
        when(horario.descripcion()).thenReturn("de lunes a viernes de 8:00 a 18:00 y los sábados de 8:00 a 13:00");

        ChatResponse r = service.responder("¿Cuál es la dirección?", "u-fuera", "");

        assertThat(r.asesorDisponible()).isFalse();
        assertThat(r.answer()).contains("Lurín").contains("fuera del horario de atención")
                .contains("déjanos tus datos").doesNotContain("botón");
    }

    @Test
    void siTambienPidePrecioVaPorLaRutaDePrecios() {
        when(vectores.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        service.responder("¿Dónde puedo comprar y cuánto cuesta el alimento para cuyes?", "u-precio", "");

        verify(interacciones, never()).registrar(any(), any(), any(), eq(CaminoConsulta.UBICACION), any(), anyLong());
    }

    @Test
    void laRespuestaFijaNoEntraEnLaRevisionDePrecision() {
        Interaccion fija = Interaccion.builder().camino(CaminoConsulta.UBICACION).build();
        assertThat(InteraccionService.esRevisable(fija)).isFalse();
    }
}
