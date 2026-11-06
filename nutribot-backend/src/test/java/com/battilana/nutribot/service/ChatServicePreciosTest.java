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
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * DEF-18: el bot no puede dar un precio que no figure en el contexto. Caso real (regresion HU05,
 * 04/11): "¿y cuanto cuesta?" sobre el concentrado para ganado vacuno lechero (precio VARIABLE)
 * respondio "S/ 95.00 es el precio variable...", copiando el monto del ejemplo del prompt.
 */
@DisplayName("ChatService — verificación de montos (DEF-18)")
class ChatServicePreciosTest {

    private static final String BLOQUE_VACUNO = """
            PRODUCTO: Concentrado para ganado vacuno lechero
            Presentacion del concentrado para ganado vacuno lechero: saco de 50 kg
            Tipo de precio del concentrado para ganado vacuno lechero: VARIABLE
            Precio del concentrado para ganado vacuno lechero: variable, sujeto a la cotizacion del maiz y la soya. Se debe consultar el precio vigente con un asesor.""";
    private static final String BLOQUE_PONEDORAS = """
            PRODUCTO: Alimento balanceado para gallinas ponedoras
            Tipo de precio del alimento para gallinas ponedoras: FIJO
            Precio del alimento balanceado para gallinas ponedoras: S/ 95.00
            Disponibilidad del alimento balanceado para gallinas ponedoras: En stock""";

    @Test
    void detectaElMontoInventadoDelCasoReal() {
        assertThat(ChatService.montosInventados(
                "S/ 95.00 es el precio variable del concentrado para ganado vacuno lechero.", BLOQUE_VACUNO))
                .containsExactly("95.00");
    }

    @Test
    void aceptaLosMontosQueSiEstanEnElContextoEnCualquierFormato() {
        assertThat(ChatService.montosInventados("Cuesta S/ 95.00.", BLOQUE_PONEDORAS)).isEmpty();
        assertThat(ChatService.montosInventados("Cuesta S/95.", BLOQUE_PONEDORAS)).isEmpty();
        assertThat(ChatService.montosInventados("Cuesta S/ 95,00", BLOQUE_PONEDORAS)).isEmpty();
        assertThat(ChatService.montosInventados("La dosis es de 110 a 120 gramos.", BLOQUE_VACUNO)).isEmpty();
        assertThat(ChatService.montos("Total S/ 1,250.50 y S/ 72.00")).hasSize(2);
    }

    @Test
    void unMontoQueElBotYaDijoEnLaConversacionNoEsInventado() {
        String historial = "S/ 95.00 ¿Deseas que te derive con un asesor para concretar tu compra?";
        assertThat(ChatService.montosInventados("El saco de S/ 95.00 trae 40 kg.", BLOQUE_VACUNO + " " + historial)).isEmpty();
    }

    @Test
    void laRespuestaSeguraDependeDelTipoDePrecio() {
        assertThat(ChatService.respuestaSinMonto(BLOQUE_VACUNO)).contains("variable").doesNotContain("S/");
        assertThat(ChatService.respuestaSinMonto("PRODUCTO: algo sin precio")).contains("No puedo confirmarte").doesNotContain("S/");
    }

    @Test
    void siElModeloInventaElMontoSeReemplazaPorLaRespuestaSegura() {
        ChatClient contexto = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(contexto.prompt().user(anyString()).advisors(any(Consumer.class)).call().content())
                .thenReturn("S/ 95.00 es el precio variable del concentrado para ganado vacuno lechero.");
        ChatService service = servicio(contexto, BLOQUE_VACUNO);

        ChatResponse r = service.responder("¿Cuánto cuesta el concentrado para ganado vacuno lechero?", "p-vacuno", "");

        assertThat(r.answer()).doesNotContain("95").contains("variable");
        assertThat(r.accion()).isEqualTo("BOTON");
    }

    @Test
    void unPrecioCorrectoSeRespondeIgualQueAntes() {
        ChatClient contexto = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(contexto.prompt().user(anyString()).advisors(any(Consumer.class)).call().content())
                .thenReturn("El alimento balanceado para gallinas ponedoras cuesta S/ 95.00.");
        ChatService service = servicio(contexto, BLOQUE_PONEDORAS);

        ChatResponse r = service.responder("¿Cuánto cuesta el alimento para gallinas ponedoras?", "p-ponedoras", "");

        assertThat(r.answer()).contains("S/ 95.00");
        assertThat(r.accion()).isEqualTo("PREGUNTAR");
    }

    private ChatService servicio(ChatClient contexto, String bloque) {
        VectorStore vectores = mock(VectorStore.class);
        when(vectores.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document(bloque, Map.of("source", "lista-precios-battilana.txt"))));
        return new ChatService(mock(ChatClient.class), mock(ChatClient.class), contexto, vectores,
                mock(InteraccionService.class), mock(ChatMemory.class), mock(HorarioAtencion.class));
    }
}
