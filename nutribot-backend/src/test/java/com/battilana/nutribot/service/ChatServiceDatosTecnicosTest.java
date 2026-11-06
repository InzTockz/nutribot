package com.battilana.nutribot.service;

import com.battilana.nutribot.dto.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * DEF-19: el bot no puede dar un porcentaje que no figure en la ficha tecnica. Caso real (regresion
 * HU05, 05/11): "¿Cuanta proteina tiene el alimento para broiler?" -> "95% de proteina cruda",
 * tomando el 95 de "calcio 0.95%". Correcto: 22%.
 */
@DisplayName("ChatService — verificación de porcentajes de las fichas técnicas (DEF-19)")
class ChatServiceDatosTecnicosTest {

    /** Texto ANTERIOR de la ficha (una sola frase): asi lo leia el modelo cuando fallo. */
    private static final String FICHA_ANTERIOR = """
            La composicion garantizada del alimento de inicio para pollos broiler es: proteina cruda
            22%, energia metabolizable 3,000 kcal/kg, calcio 0.95%, fosforo disponible 0.45%, grasa
            5.0% y fibra cruda maxima 4.0%.""";

    @Test
    void detectaElPorcentajeInventadoDelCasoReal() {
        assertThat(ChatService.porcentajesInventados("El alimento para broiler contiene un 95% de proteína cruda.", FICHA_ANTERIOR))
                .containsExactly("95%");
    }

    @Test
    void aceptaLosPorcentajesQueSiEstanEnLaFicha() {
        assertThat(ChatService.porcentajesInventados("Tiene una proteína cruda del 22%.", FICHA_ANTERIOR)).isEmpty();
        assertThat(ChatService.porcentajesInventados("Proteína 22 %, calcio 0.95 % y grasa 5%.", FICHA_ANTERIOR)).isEmpty();
        assertThat(ChatService.porcentajesInventados("Grasa de 5,0%.", FICHA_ANTERIOR)).isEmpty();
    }

    @Test
    void elNoventaYCincoDelCalcioNoSeConfundeConNoventaYCinco() {
        assertThat(ChatService.porcentajes("calcio 0.95%")).hasSize(1)
                .allSatisfy(p -> assertThat(p).isEqualByComparingTo("0.95"));
        assertThat(ChatService.porcentajes("de 20% a 22% de proteina")).hasSize(2);
    }

    @Test
    void siElModeloInventaElPorcentajeSeReemplazaPorLaRespuestaSegura() {
        // la ficha anterior tiene "calcio 0.95%": el 95 no esta como porcentaje propio
        ChatResponse r = responder("El alimento para broiler contiene un 95% de proteína cruda.");

        assertThat(r.answer()).isEqualTo(ChatService.DATO_NO_CONFIRMADO).doesNotContain("95");
        assertThat(r.accion()).isEqualTo("BOTON");
    }

    @Test
    void unPorcentajeCorrectoSeRespondeIgualQueAntes() {
        ChatResponse r = responder("El alimento para broiler de Battilana contiene un 22% de proteína cruda.");

        assertThat(r.answer()).contains("22%");
        assertThat(r.accion()).isEqualTo("NINGUNA");
    }

    private ChatResponse responder(String respuestaDelModelo) {
        // Desde el DEF-21 las preguntas de nutrientes van por el cliente de contexto acotado
        // (el mismo de los precios), no por el cliente general con RAG.
        ChatClient general = mock(ChatClient.class);
        ChatClient contexto = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(contexto.prompt().user(anyString()).advisors(any(Consumer.class)).call().content()).thenReturn(respuestaDelModelo);
        VectorStore vectores = mock(VectorStore.class);
        when(vectores.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(new Document(FICHA_ANTERIOR)));
        ChatService service = new ChatService(general, mock(ChatClient.class), contexto, vectores,
                mock(InteraccionService.class), mock(ChatMemory.class), mock(HorarioAtencion.class));
        return service.responder("¿Cuánta proteína tiene el alimento para broiler?", "dt-" + respuestaDelModelo.length(), "");
    }
}
