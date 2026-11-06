package com.battilana.nutribot.service;

import com.battilana.nutribot.dto.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
 * DEF-21: las preguntas de ficha tecnica reciben solo el bloque del producto consultado. Caso real
 * (05/11): "¿Cuanta proteina tiene el alimento para codornices en postura?" -> "16,5%" (gallinas
 * ponedoras). Correcto: 20%.
 */
@DisplayName("ChatService — datos de ficha técnica por producto (DEF-21)")
class ChatServiceFichaTest {

    private static final Document CODORNICES = new Document("""
            ¿Cual es la composicion del alimento balanceado para codornices en postura?
            - Proteina cruda del alimento balanceado para codornices en postura: 20%
            - Calcio del alimento balanceado para codornices en postura: 3.0%""");
    private static final Document PONEDORAS = new Document("""
            Composicion nutricional (garantia) del alimento balanceado para gallinas ponedoras:
            - Proteina cruda del alimento balanceado para gallinas ponedoras: 16.5%
            - Calcio del alimento balanceado para gallinas ponedoras: 3.8%""");

    private final ChatClient general = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final ChatClient contexto = mock(ChatClient.class, RETURNS_DEEP_STUBS);

    private ChatResponse preguntar(String respuestaDelModelo) {
        when(contexto.prompt().user(anyString()).advisors(any(Consumer.class)).call().content()).thenReturn(respuestaDelModelo);
        VectorStore vectores = mock(VectorStore.class);
        when(vectores.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(PONEDORAS, CODORNICES));
        ChatService service = new ChatService(general, mock(ChatClient.class), contexto, vectores,
                mock(InteraccionService.class), mock(ChatMemory.class), mock(HorarioAtencion.class));
        return service.responder("¿Cuánta proteína tiene el alimento para codornices en postura?", "f-" + respuestaDelModelo.length(), "");
    }

    @Test
    void alModeloSoloLeLlegaElBloqueDelProductoConsultado() {
        preguntar("El alimento balanceado para codornices en postura contiene 20% de proteína cruda.");

        ArgumentCaptor<String> enviado = ArgumentCaptor.forClass(String.class);
        verify(contexto.prompt(), atLeastOnce()).user(enviado.capture());
        String ultimo = enviado.getAllValues().get(enviado.getAllValues().size() - 1);
        assertThat(ultimo).contains("codornices en postura: 20%").doesNotContain("16.5%").doesNotContain("ponedoras");
    }

    @Test
    void unDatoDeOtroProductoSeBloquea() {
        ChatResponse r = preguntar("El alimento balanceado para gallinas ponedoras contiene una proteína cruda del 16,5%.");

        assertThat(r.answer()).isEqualTo(ChatService.DATO_NO_CONFIRMADO);
        assertThat(r.accion()).isEqualTo("BOTON");
    }

    @Test
    void elDatoCorrectoSeRespondeIgualQueAntes() {
        ChatResponse r = preguntar("El alimento balanceado para codornices en postura contiene 20% de proteína cruda.");

        assertThat(r.answer()).contains("20%");
        assertThat(r.accion()).isEqualTo("NINGUNA");
    }
}
