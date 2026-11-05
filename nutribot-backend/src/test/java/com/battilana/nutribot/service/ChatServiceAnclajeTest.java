package com.battilana.nutribot.service;

import com.battilana.nutribot.dto.ChatResponse;
import com.battilana.nutribot.model.CaminoConsulta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DEF-16: si el tema de la pregunta no existe en la base de conocimiento, el bot no debe
 * inventar: responde que no tiene la informacion y ofrece un asesor (camino SIN_INFO).
 * Caso real del E2E (CP-23): "¿Que es un alimento para dragones?" obtuvo similitud 0.723.
 */
@DisplayName("ChatService — anclaje del tema en la base de conocimiento (DEF-16)")
class ChatServiceAnclajeTest {

    private final ChatClient chat = mock(ChatClient.class);
    private final ChatClient reformulacion = mock(ChatClient.class);
    private final ChatClient contexto = mock(ChatClient.class);
    private final VectorStore vectores = mock(VectorStore.class);
    private final InteraccionService interacciones = mock(InteraccionService.class);
    private final HorarioAtencion horario = mock(HorarioAtencion.class);
    private final ChatService service = new ChatService(
            chat, reformulacion, contexto, vectores, interacciones, mock(ChatMemory.class), horario);

    private static final Document FAQ = new Document(
            "¿Que diferencia hay entre alimento de inicio, crecimiento y acabado? Cada etapa tiene su formula.");
    private static final Document UBICACION = new Document(
            "UBICACION. ¿Donde esta ubicada Battilana Nutricion SAC? En la Av. Principal 123, Lima.");
    private static final Document ENVIOS = new Document(
            "¿Realizan envios? Si, realizamos envios a Lima y a provincia mediante agencia.");

    @Test
    void unTemaQueNoExisteVaASinInfoSinLlamarAlModelo() {
        when(vectores.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(FAQ));

        ChatResponse r = service.responder("¿Qué es un alimento para dragones?", "c-dragon", "");

        assertThat(r.accion()).isEqualTo("BOTON");
        assertThat(r.answer()).isEqualTo(ChatService.SIN_INFORMACION);
        verify(interacciones).registrar(any(), any(), any(), org.mockito.ArgumentMatchers.eq(CaminoConsulta.SIN_INFO), any(), anyLong());
        verifyNoInteractions(chat, contexto);   // el modelo ni siquiera llega a responder
    }

    @Test
    void segundaOportunidadEncuentraElTemaAunqueLaPrimeraBusquedaNoLoTraiga() {
        // primera busqueda: un fragmento del FAQ que no habla de envios;
        // segunda busqueda, solo con las palabras clave: aparece el fragmento de envios
        when(vectores.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(ENVIOS));

        assertThat(service.temaEnBase(List.of(FAQ), "¿Hacen envíos a provincias?", "envios provincias")).isTrue();

        ArgumentCaptor<SearchRequest> busqueda = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectores).similaritySearch(busqueda.capture());
        assertThat(busqueda.getValue().getQuery()).isEqualTo("envios provincias");
    }

    @Test
    void losSinonimosUnenDireccionConUbicacion() {
        // E2E 04/11: el documento solo dice "UBICACION"; el cliente pregunta por la "direccion"
        assertThat(service.temaEnBase(List.of(UBICACION), "¿Cuál es su dirección?", "direccion")).isTrue();
        verifyNoInteractions(vectores);
        assertThat(ChatService.equivalentes("direccion")).contains("ubic");
        assertThat(ChatService.equivalentes("dragones")).containsExactly("dragon");
    }

    @Test
    void siElTemaYaEstaEnLoRecuperadoNoHaceUnaSegundaBusqueda() {
        assertThat(service.temaEnBase(List.of(UBICACION), "¿Dónde están ubicados?", "ubicados")).isTrue();
        verifyNoInteractions(vectores);
    }

    @Test
    void sinPalabrasClaveNoSePuedeJuzgarYSeDejaResponder() {
        assertThat(service.temaEnBase(List.of(FAQ), "Hola", "hola")).isTrue();
        assertThat(service.temaEnBase(List.of(FAQ), "¿Qué alimentos tienen?", "alimentos")).isTrue();
        verifyNoInteractions(vectores);
    }

    @Test
    void lasPalabrasClaveIgnoranInterrogativosVerbosYGenericas() {
        assertThat(ChatService.palabrasClave("¿Qué es un alimento para dragones?")).containsExactly("dragones");
        assertThat(ChatService.palabrasClave("¿Qué dosis necesita una gallina ponedora?")).containsExactly("gallina", "ponedora");
        assertThat(ChatService.palabrasClave("Que tipo de alimentos actualmente tienen en venta?")).isEmpty();
    }

    @Test
    void laRaizUneLasPalabrasDeLaMismaFamilia() {
        assertThat(ChatService.raizTema("ubicados")).isEqualTo("ubic");
        assertThat(ChatService.raizTema("ubicada")).isEqualTo("ubic");
        assertThat(ChatService.raizTema("ubicacion")).isEqualTo("ubic");
        assertThat(ChatService.raizTema("ponedoras")).isEqualTo(ChatService.raizTema("ponedora"));
        assertThat(ChatService.raizTema("cuyes")).isEqualTo("cuy");
        assertThat(ChatService.raizTema("dragones")).isEqualTo("dragon");
        assertThat(ChatService.mencionaAlguna(List.of(FAQ), List.of("dragones"))).isFalse();
    }

    @Test
    void elDetectorReconoceLasNegacionesInventadas() {
        assertThat(ChatService.respuestaIncierta(
                "El alimento para dragones es un alimento balanceado... No es un producto que se ofrece en Battilana Nutricion SAC.")).isTrue();
        assertThat(ChatService.respuestaIncierta("No vendemos ese producto.")).isTrue();
        assertThat(ChatService.respuestaIncierta("La dosis necesaria para una gallina ponedora es de 110 a 120 gramos por ave por día.")).isFalse();
    }
}
