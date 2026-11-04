package com.battilana.nutribot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 4 (deuda tecnica): pruebas unitarias de la logica determinista de ChatService
 * construida en el Sprint 3 (HU02, HU05) y de las correcciones DEF-06, DEF-07, DEF-08 y DEF-09.
 * No necesitan Ollama, Qdrant ni MySQL: prueban el codigo que decide, no al modelo.
 */
@DisplayName("ChatService — análisis de la consulta")
class ChatServiceAnalisisTest {

    @Nested
    @DisplayName("normalizar")
    class Normalizar {
        @Test
        void quitaTildesYMayusculas() {
            assertThat(ChatService.normalizar("¿Cuánto CUESTA la Ración?")).isEqualTo("¿cuanto cuesta la racion?");
        }
    }

    @Nested
    @DisplayName("extraerSujeto (DEF-06)")
    class ExtraerSujeto {
        @Test
        void quitaInterrogativosVerbosEIntencion() {
            assertThat(ChatService.extraerSujeto("¿Qué dosis necesita una gallina ponedora?")).isEqualTo("gallina ponedora");
        }

        @Test
        void conservaElProductoDeUnaPreguntaDeManejo() {
            assertThat(ChatService.extraerSujeto("¿Cómo se almacena el alimento balanceado?")).isEqualTo("alimento balanceado");
        }

        @Test
        void noArrastraLaPalabraDeIntencionPrecio() {
            assertThat(ChatService.extraerSujeto("¿Cuál es el precio del alimento para cuyes?")).isEqualTo("alimento cuyes");
        }

        @Test
        void preguntaVaciaDevuelveVacio() {
            assertThat(ChatService.extraerSujeto("   ")).isEmpty();
            assertThat(ChatService.extraerSujeto(null)).isEmpty();
        }

        @Test
        void siNoQuedaNadaUtilDevuelveLaPreguntaLimpia() {
            assertThat(ChatService.extraerSujeto("¿Qué es?")).isEqualTo("que es");
        }
    }

    @Nested
    @DisplayName("esSeguimiento y esReformulacion (HU05, DEF-07)")
    class TipoDeConsulta {
        @Test
        void detectaSeguimientos() {
            assertThat(ChatService.esSeguimiento(ChatService.normalizar("¿y cuánto cuesta?"))).isTrue();
            assertThat(ChatService.esSeguimiento(ChatService.normalizar("y la presentación"))).isTrue();
            assertThat(ChatService.esSeguimiento(ChatService.normalizar("¿Cuánto cuesta ese producto?"))).isTrue();
        }

        @Test
        void unaPreguntaNuevaNoEsSeguimiento() {
            assertThat(ChatService.esSeguimiento(ChatService.normalizar("¿Cuál es el horario de atención?"))).isFalse();
            assertThat(ChatService.esSeguimiento(ChatService.normalizar("¿Qué dosis necesita una gallina ponedora?"))).isFalse();
        }

        @Test
        void detectaReformulaciones() {
            assertThat(ChatService.esReformulacion(ChatService.normalizar("¿Puedes explicarlo más simple?"))).isTrue();
            assertThat(ChatService.esReformulacion(ChatService.normalizar("No entendí"))).isTrue();
        }

        @Test
        void unaPreguntaDePrecioNoEsReformulacion() {
            assertThat(ChatService.esReformulacion(ChatService.normalizar("¿Cuál es el precio del alimento?"))).isFalse();
        }
    }

    @Nested
    @DisplayName("raiz")
    class Raiz {
        @Test
        void unificaSingularYPlural() {
            assertThat(ChatService.raiz("gallinas")).isEqualTo("gallina");
            assertThat(ChatService.raiz("ponedoras")).isEqualTo("ponedora");
            assertThat(ChatService.raiz("productores")).isEqualTo("productor");
        }

        @Test
        void noRecortaPalabrasCortas() {
            assertThat(ChatService.raiz("mes")).isEqualTo("mes");
            assertThat(ChatService.raiz("res")).isEqualTo("res");
        }
    }

    @Nested
    @DisplayName("contextoDelProducto (DEF-08, DEF-09)")
    class ContextoDelProducto {

        private final List<Document> lista = List.of(new Document("""
                PRODUCTO: Alimento balanceado para gallinas ponedoras
                Presentacion del alimento para gallinas ponedoras: saco de 40 kg
                Precio del alimento balanceado para gallinas ponedoras: S/ 95.00

                PRODUCTO: Alimento de inicio para pollos broiler
                Presentacion del alimento de inicio para pollos broiler: saco de 40 kg
                Precio del alimento de inicio para pollos broiler: S/ 102.00
                """));

        @Test
        void soloEntregaElBloqueDelProductoConsultado() {
            String contexto = ChatService.contextoDelProducto(lista, "gallina ponedora");
            assertThat(contexto).contains("S/ 95.00").doesNotContain("S/ 102.00");
        }

        @Test
        void unaPalabraQueNoEstaEnElCatalogoNoAnulaElFiltro() {
            // DEF-09: "barato" no aparece en el catalogo; exigir todas las palabras anularia el filtro.
            // Con el puntaje maximo, igual se elige el bloque correcto.
            String contexto = ChatService.contextoDelProducto(lista, "barato gallina ponedora");
            assertThat(contexto).contains("S/ 95.00").doesNotContain("S/ 102.00");
        }

        @Test
        void siElSujetoNoApareceDevuelveTodoElContexto() {
            String contexto = ChatService.contextoDelProducto(lista, "alpaca");
            assertThat(contexto).contains("S/ 95.00").contains("S/ 102.00");
        }

        @Test
        void separaLosBloquesPorProducto() {
            assertThat(ChatService.separarEnBloques(lista.get(0).getText())).hasSize(2);
        }
    }
}
