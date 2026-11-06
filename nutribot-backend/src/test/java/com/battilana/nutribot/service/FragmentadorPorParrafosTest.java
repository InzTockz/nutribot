package com.battilana.nutribot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** DEF-20: los fragmentos respetan parrafos y titulos; nunca parten un numero. */
@DisplayName("FragmentadorPorParrafos — fragmentación de la base de conocimiento (DEF-20)")
class FragmentadorPorParrafosTest {

    private static final String RELLENO = "Texto de relleno para ocupar espacio en el fragmento. ".repeat(12);

    private static final String FICHAS = """
            ==================================================
            ALIMENTO DE INICIO PARA POLLOS BROILER
            ==================================================

            ¿Que es el alimento de inicio para pollos broiler?
            %s

            ==================================================
            ALIMENTO PARA LECHONES EN DESTETE
            ==================================================

            ¿Cual es la composicion del alimento para lechones en destete?
            - Lisina del alimento para lechones en destete: 1.45%%
            - Calcio del alimento para lechones en destete: 0.85%%
            - Grasa del alimento para lechones en destete: 6.0%%
            """.formatted(RELLENO);

    private List<Document> fragmentar(String texto, int max) {
        return FragmentadorPorParrafos.fragmentar(
                List.of(new Document(texto, Map.of("source", "fichas.txt"))), max);
    }

    @Test
    void nuncaParteUnNumeroDecimal() {
        for (Document f : fragmentar(FICHAS, 900)) {
            assertThat(f.getText()).doesNotStartWith("45%").doesNotEndWith("1.");
        }
        assertThat(fragmentar(FICHAS, 900)).anySatisfy(f -> assertThat(f.getText()).contains("Lisina del alimento para lechones en destete: 1.45%"));
    }

    @Test
    void cadaTituloAbreUnFragmentoJuntoASuContenido() {
        List<Document> fragmentos = fragmentar(FICHAS, 900);
        Document lechones = fragmentos.stream().filter(f -> f.getText().contains("LECHONES EN DESTETE")).findFirst().orElseThrow();

        assertThat(lechones.getText()).startsWith("=====").contains("Grasa del alimento para lechones en destete: 6.0%");
        assertThat(fragmentos.get(0).getText()).doesNotContain("LECHONES");
    }

    @Test
    void respetaElTamanoMaximoYConservaLosMetadatos() {
        List<Document> fragmentos = fragmentar(FICHAS, 900);

        assertThat(fragmentos).hasSizeGreaterThan(1);
        assertThat(fragmentos).allSatisfy(f -> {
            assertThat(f.getText().length()).isLessThanOrEqualTo(900);
            assertThat(f.getMetadata()).containsEntry("source", "fichas.txt");
        });
    }

    @Test
    void unBloqueMuyLargoSeParteSoloPorLineasOEspacios() {
        String largo = ("- Proteina cruda del alimento: 22%\n").repeat(60);
        for (Document f : fragmentar(largo, 300)) {
            assertThat(f.getText().length()).isLessThanOrEqualTo(300);
            assertThat(f.getText()).startsWith("- Proteina").endsWith("22%");
        }
    }

    @Test
    void unTituloNuncaQuedaSoloAunqueSuBloqueSeaGrande() {
        String texto = "=====\nTITULO DE PRUEBA\n=====\n\n" + "Contenido largo del titulo. ".repeat(40);
        List<Document> fragmentos = fragmentar(texto, 900);
        assertThat(fragmentos.get(0).getText()).startsWith("=====").contains("Contenido largo");
    }

    @Test
    void reconoceLosTitulos() {
        assertThat(FragmentadorPorParrafos.esTitulo("=====\nUBICACION, PLANTA Y PUNTOS DE VENTA\n=====")).isTrue();
        assertThat(FragmentadorPorParrafos.esTitulo("¿Cual es la direccion?\nLa planta esta en Lurin.")).isFalse();
        assertThat(FragmentadorPorParrafos.esTitulo("- Calcio: 0.95%")).isFalse();
    }
}
