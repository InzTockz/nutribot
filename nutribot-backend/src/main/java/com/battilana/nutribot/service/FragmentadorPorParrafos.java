package com.battilana.nutribot.service;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * DEF-20: fragmenta los documentos de la base de conocimiento respetando su estructura.
 *
 * Antes se usaba TokenTextSplitter (300 tokens), que corta cada bloque en el ULTIMO signo de
 * puntuacion que encuentra. En "Lisina ...: 1.45%" ese signo fue el punto DECIMAL: el fragmento
 * de lechones empezaba con un "45%" huerfano y el modelo respondia "45% de grasa" (correcto: 6.0%).
 *
 * Reglas (de la mas importante a la menos):
 *  1. Se separa por lineas en blanco: un titulo, una pregunta con su respuesta o un bloque
 *     "PRODUCTO: ..." nunca se parte.
 *  2. Los bloques seguidos se juntan hasta maxCaracteres (~ el tamano anterior).
 *  3. Un titulo (lineas en mayusculas o de "=====") siempre abre un fragmento nuevo, para que
 *     quede junto a su contenido y no al final del fragmento anterior; un titulo nunca queda
 *     solo (si con su primer bloque se pasa un poco del maximo, se tolera).
 *  4. Si un bloque supera el maximo se parte por lineas; si una linea lo supera, por espacios.
 *     Nunca se corta dentro de una palabra ni de un numero.
 */
final class FragmentadorPorParrafos {

    static final int MAX_CARACTERES = 1100;

    private FragmentadorPorParrafos() {
    }

    static List<Document> fragmentar(List<Document> documentos) {
        return fragmentar(documentos, MAX_CARACTERES);
    }

    static List<Document> fragmentar(List<Document> documentos, int maxCaracteres) {
        List<Document> fragmentos = new ArrayList<>();
        for (Document documento : documentos) {
            String texto = documento.getText();
            if (texto == null || texto.isBlank()) {
                continue;
            }
            StringBuilder actual = new StringBuilder();
            for (String bloque : texto.replace("\r\n", "\n").split("\\n\\s*\\n")) {
                String limpio = bloque.strip();
                if (limpio.isEmpty()) {
                    continue;
                }
                for (String pieza : partirSiEsLargo(limpio, maxCaracteres)) {
                    boolean noCabe = actual.length() + 2 + pieza.length() > maxCaracteres;
                    // un titulo solo no se cierra: se queda con su contenido aunque se pase un poco
                    boolean soloTitulo = esTitulo(actual.toString());
                    if (actual.length() > 0 && (esTitulo(pieza) || (noCabe && !soloTitulo))) {
                        fragmentos.add(nuevo(actual, documento));
                        actual.setLength(0);
                    }
                    if (actual.length() > 0) {
                        actual.append("\n\n");
                    }
                    actual.append(pieza);
                }
            }
            if (actual.length() > 0) {
                fragmentos.add(nuevo(actual, documento));
            }
        }
        return fragmentos;
    }

    /** Un titulo: todas sus lineas son "=====" o van en MAYUSCULAS (sin "?"). */
    static boolean esTitulo(String bloque) {
        for (String linea : bloque.split("\\n")) {
            String l = linea.strip();
            if (l.isEmpty() || l.matches("[=\\-_*#]{3,}")) {
                continue;
            }
            if (l.contains("?") || !l.equals(l.toUpperCase()) || !l.matches(".*\\p{L}.*")) {
                return false;
            }
        }
        return true;
    }

    /** Parte un bloque demasiado largo por lineas y, si hace falta, una linea por espacios. */
    static List<String> partirSiEsLargo(String bloque, int max) {
        List<String> piezas = new ArrayList<>();
        if (bloque.length() <= max) {
            piezas.add(bloque);
            return piezas;
        }
        StringBuilder actual = new StringBuilder();
        for (String linea : bloque.split("\\n")) {
            for (String parte : partirLinea(linea, max)) {
                if (actual.length() > 0 && actual.length() + 1 + parte.length() > max) {
                    piezas.add(actual.toString());
                    actual.setLength(0);
                }
                if (actual.length() > 0) {
                    actual.append('\n');
                }
                actual.append(parte);
            }
        }
        if (actual.length() > 0) {
            piezas.add(actual.toString());
        }
        return piezas;
    }

    private static List<String> partirLinea(String linea, int max) {
        List<String> partes = new ArrayList<>();
        String resto = linea;
        while (resto.length() > max) {
            int corte = resto.lastIndexOf(' ', max);
            if (corte <= 0) {
                corte = max;   // palabra gigante sin espacios: unico caso de corte forzado
            }
            partes.add(resto.substring(0, corte).strip());
            resto = resto.substring(corte).strip();
        }
        if (!resto.isEmpty()) {
            partes.add(resto);
        }
        return partes;
    }

    private static Document nuevo(StringBuilder texto, Document original) {
        return new Document(texto.toString(), new HashMap<>(original.getMetadata()));
    }
}
