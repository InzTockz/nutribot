package com.battilana.nutribot.service;

import com.battilana.nutribot.dto.ChatResponse;
import com.battilana.nutribot.model.CaminoConsulta;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Logica del chat (HU01) con memoria de conversacion (HU05) y derivacion a un asesor.
 * El asesor se ofrece SOLO cuando:
 *   (a) el cliente lo pide explicitamente (HU07), o
 *   (b) no hay informacion suficiente en la base de conocimiento -> baja certeza (HU08).
 *
 * Sprint 4: cada consulta se registra en la tabla interaccion (tiempo, camino y accion),
 * que alimenta el panel de metricas (HU12) y la valoracion de respuestas (HU13).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatClient nutribotChatClient;
    private final ChatClient nutribotReformulacionChatClient;
    private final ChatClient nutribotContextoChatClient;
    private final VectorStore vectorStore;
    private final InteraccionService interaccionService;
    private final ChatMemory nutribotChatMemory;   // Sprint 4: para liberar las conversaciones inactivas
    private final HorarioAtencion horarioAtencion; // DEF-15: el backend decide si hay asesor

    /** Resultado interno de procesar una consulta: la respuesta, la accion y el camino seguido. */
    private record Resultado(String answer, String accion, CaminoConsulta camino) {
    }

    @Value("${nutribot.rag.similarity-threshold:0.4}")
    private double similarityThreshold;

    /** Sprint 4 (deuda tecnica): fragmentos recuperados por consulta, antes fijo en 6. */
    @Value("${nutribot.rag.top-k:6}")
    private int topK;

    /** Sprint 4 (deuda tecnica): minutos sin mensajes tras los cuales se libera una conversacion. */
    @Value("${nutribot.chat.inactividad-minutos:120}")
    private long inactividadMinutos;

    /**
     * DEF-10: nombre del documento que contiene la lista de precios oficial. En las consultas de
     * precio la busqueda se restringe a el por metadato "source", para que los fragmentos de la
     * ficha tecnica o del FAQ no ocupen los cupos de topK y desplacen al bloque del producto.
     */
    @Value("${nutribot.rag.price-document:lista-precios-battilana.txt}")
    private String priceDocument;

    // Intencion del cliente de hablar con una persona
    private static final List<String> PIDE_ASESOR = List.of(
            "asesor", "humano", "una persona", "vendedor", "agente", "hablar con alguien");

    /**
     * DEF-17: preguntas por la direccion, la tienda o donde comprar. Battilana no tiene tienda ni
     * punto de venta: todo se vende por WhatsApp. Se responde con un mensaje fijo (como cuando se
     * pide un asesor) porque el modelo, aun con el dato delante, lo ignoraba o inventaba
     * "sucursales" y una "pagina web".
     */
    private static final List<String> PIDE_UBICACION = List.of(
            "direccion", "ubicad", "ubicacion", "donde estan", "donde queda", "donde se encuentran",
            "tienda", "punto de venta", "puntos de venta", "sucursal", "donde compro",
            "donde puedo comprar", "donde comprar", "donde venden", "donde los encuentro",
            "atienden en persona", "visitarlos", "ir a comprar");

    /**
     * DEF-21: preguntas por un dato de la ficha tecnica (nutrientes). Como en los precios (DEF-08),
     * se le entrega al modelo SOLO el bloque del producto consultado: con fragmentos de varias
     * fichas parecidas mezclaba productos ("codornices" -> "16.5% de las gallinas ponedoras").
     */
    private static final List<String> ES_CONSULTA_FICHA = List.of(
            "proteina", "grasa", "calcio", "fosforo", "fibra", "lisina", "metionina", "energia",
            "vitamina", "composicion", "nutriente", "humedad", "ceniza", "kcal");

    /** DEF-17: si ademas pide un precio, manda la ruta de precios (no la respuesta fija). */
    private static final List<String> PIDE_MONTO = List.of(
            "precio", "costo", "cuesta", "cuestan", "vale", "valen", "cobran", "cotiz");

    /** DEF-17: texto oficial (se puede cambiar en application.yml: nutribot.empresa.mensaje-ubicacion). */
    static final String MENSAJE_UBICACION =
            "Battilana Nutrición no cuenta con tienda ni punto de venta al público: todas las ventas se "
            + "atienden por WhatsApp con un asesor, que toma tu pedido, confirma el precio y la disponibilidad "
            + "y coordina la entrega a domicilio o en granja. Nuestra planta central se encuentra en Calle Los "
            + "Claveles Mz. A Lote A-2, Urb. Fundo Casco Viejo, Lurín, Lima.";

    @Value("${nutribot.empresa.mensaje-ubicacion:}")
    private String mensajeUbicacionConfigurado;

    // Consultas de precio/compra
    private static final List<String> ES_CONSULTA_PRECIO = List.of(
            "precio", "costo", "cuesta", "cuestan", "vale", "valen", "valor", "sale en",
            "cobran", "cobra", "pagar", "stock", "disponib", "comprar", "cotiz", "venden");

    // Senales de incertidumbre en la respuesta (precio variable / sin precio / sin stock / desconocido)
    private static final List<String> INCERTIDUMBRE = List.of(
            "variable", "consultar", "bajo pedido", "sin stock", "no hay stock", "agotado",
            "no especifica", "no tengo esa informacion", "no encontre", "no esta disponible",
            // DEF-16: negaciones que el modelo inventa cuando no encuentra el dato
            // ("No es un producto que se ofrece en Battilana"); tambien deben derivar al asesor
            "no es un producto", "no se ofrece", "no ofrecemos", "no ofrece", "no vendemos",
            "no se vende", "no contamos", "no manejamos", "no trabajamos");

    /** DEF-16: respuesta estandar cuando la base de conocimiento no tiene el dato. */
    static final String SIN_INFORMACION =
            "No encontre esa informacion en mi base de conocimiento. Si deseas, puedo derivarte con un asesor.";

    /**
     * DEF-16: palabras demasiado generales para decir de QUE trata una pregunta. Aparecen en casi
     * todos los documentos ("alimento", "balanceado") o son cortesia ("hola", "gracias"), asi que
     * no sirven para comprobar que el tema exista en la base de conocimiento.
     */
    private static final Set<String> PALABRAS_GENERICAS = Set.of(
            "alimento", "alimentos", "balanceado", "balanceados", "producto", "productos",
            "comida", "comidas", "tipo", "tipos", "battilana", "nutricion", "empresa", "sac",
            "actualmente", "ahora", "venta", "ventas", "disponible", "disponibles", "ofrecen",
            "quiero", "quisiera", "saber", "favor", "ayuda", "ayudar", "consulta", "pregunta",
            "hola", "buenas", "buenos", "dias", "tardes", "noches", "gracias", "ok", "okay",
            "entre", "otro", "otra", "otros", "otras", "mas", "muy", "bien", "algo", "todo", "todos",
            // DEF-17: verbos de intencion que no dicen el tema ("¿donde PUEDO comprar?")
            "puedo", "puedes", "podria", "podrian", "tengo", "tienes", "busco", "necesito", "hacer");

    /**
     * HU05: seguimiento de tipo REFORMULACION. El cliente no pide informacion nueva, pide la
     * misma respuesta dicha de otro modo. Se resuelve solo con el historial, SIN RAG: recuperar
     * fragmentos aqui desviaba la respuesta hacia otro tema (DEF-07).
     */
    private static final List<String> MARCAS_REFORMULACION = List.of(
            "explicalo", "explicarlo", "explicame", "explica eso", "explicar eso",
            "no entendi", "no entiendo", "no comprendo", "mas simple", "mas facil",
            "mas claro", "en palabras simples", "repite", "repitelo", "repiteme",
            "resumelo", "resumeme", "en resumen", "de nuevo", "otra vez");

    // HU05: senales de que la pregunta depende de lo dicho antes (pregunta de seguimiento)
    private static final List<String> MARCAS_SEGUIMIENTO = List.of(
            "explicalo", "explicarlo", "explicame", "explica eso", "repite", "repitelo",
            "no entendi", "mas simple", "mas facil", "resumelo", "en resumen",
            "y eso", "de eso", "ese producto", "ese alimento", "ese insumo",
            "y cuanto", "y el precio", "y la ", "y su ");

    /**
     * HU05: ultima pregunta "de tema" de cada conversacion (la ultima que NO fue de seguimiento).
     * Es la referencia correcta para contextualizar: si usaramos simplemente la pregunta anterior,
     * dos seguimientos encadenados ("¿y cuanto cuesta?" -> "¿puedes explicarlo mas simple?")
     * harian que la busqueda pierda por completo el tema original.
     */
    private final Map<String, String> temaPorConversacion = new ConcurrentHashMap<>();

    /**
     * Sprint 4 (deuda tecnica, riesgo R14): ultimo mensaje de cada conversacion. Antes, el tema y el
     * historial de cada visitante quedaban en memoria para siempre; ahora se liberan tras un periodo
     * de inactividad (ver liberarConversacionesInactivas).
     */
    private final Map<String, Instant> ultimaActividad = new ConcurrentHashMap<>();

    /**
     * Palabras que se descartan al extraer el SUJETO de la pregunta anterior.
     * Incluye interrogativos, verbos y conectores, pero tambien los sustantivos de INTENCION
     * (dosis, precio, stock...): la intencion debe aportarla la pregunta actual, no la anterior.
     * Si no se descartaran, una consulta como "¿y cuanto cuesta?" contextualizada con
     * "¿que DOSIS necesita una gallina ponedora?" arrastraria la palabra "dosis" al embedding
     * y la busqueda devolveria la ficha tecnica en lugar de la lista de precios.
     */
    private static final Set<String> PALABRAS_VACIAS = Set.of(
            // interrogativos
            "que", "cual", "cuales", "cuanto", "cuanta", "cuantos", "cuantas", "como",
            "donde", "cuando", "quien", "quienes", "porque", "por",
            // verbos frecuentes
            "necesita", "necesitan", "tiene", "tienen", "es", "son", "esta", "estan", "hay",
            "debe", "deben", "puede", "pueden", "cuesta", "cuestan", "sirve", "sirven",
            "usa", "usan", "usar", "hace", "hacen", "ofrecen", "venden", "vende", "da", "dar",
            "almacena", "almacenan", "guarda", "guardan", "recomienda", "recomiendan",
            "vale", "valen", "valor", "cobra", "cobran", "pagar", "sale", "vender", "comprar",
            // articulos, preposiciones y pronombres
            "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "al", "a",
            "para", "con", "en", "y", "o", "su", "sus", "mi", "mis", "me", "se", "lo", "les",
            // sustantivos de intencion
            "dosis", "precio", "precios", "costo", "costos", "stock", "disponibilidad",
            "uso", "almacenamiento", "informacion", "ficha", "tecnica", "dato", "datos",
            "cantidad", "consumo");

    public ChatResponse responder(String message, String conversationId, String previousQuestion) {
        // HU05: el id de conversacion es OBLIGATORIO para la memoria; si no llega, usamos uno por defecto
        final String convId = (conversationId == null || conversationId.isBlank())
                ? "default" : conversationId;

        registrarActividad(convId, Instant.now());

        // Sprint 4: se mide el tiempo de TODA la atencion (clasificacion + busqueda + modelo)
        long inicio = System.nanoTime();
        boolean asesorDisponible = horarioAtencion.disponible();   // DEF-15
        Resultado r;
        try {
            r = procesar(message, convId, previousQuestion, asesorDisponible);
        } catch (RuntimeException e) {
            // Tambien se registran los fallos (p. ej. Ollama apagado): alimentan la metrica de fiabilidad
            interaccionService.registrar(convId, message, null, CaminoConsulta.ERROR, "ERROR", milisDesde(inicio));
            throw e;
        }
        Long interaccionId = interaccionService.registrar(
                convId, message, r.answer(), r.camino(), r.accion(), milisDesde(inicio));
        return new ChatResponse(r.answer(), r.accion(), interaccionId, asesorDisponible);
    }

    /** DEF-17: no hay tienda; las ventas son por WhatsApp. El cierre depende del horario (DEF-15). */
    String mensajeUbicacion(boolean asesorDisponible) {
        String base = (mensajeUbicacionConfigurado == null || mensajeUbicacionConfigurado.isBlank())
                ? MENSAJE_UBICACION : mensajeUbicacionConfigurado.trim();
        if (asesorDisponible) {
            return base + " Puedes escribirle a un asesor con el botón de abajo.";
        }
        return base + " En este momento estamos fuera del horario de atención (" + horarioAtencion.descripcion()
                + "): déjanos tus datos en el formulario y un asesor te contactará.";
    }

    /** DEF-18: montos en soles ("S/ 95", "S/95.00", "S/ 1,250.50"). */
    private static final java.util.regex.Pattern MONTO =
            java.util.regex.Pattern.compile("S/\\.?\\s*(\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)");

    /** DEF-18: montos de la respuesta que NO aparecen en el texto de referencia ("95.00", ...). */
    static List<String> montosInventados(String respuesta, String referencia) {
        Set<java.math.BigDecimal> permitidos = montos(referencia);
        List<String> inventados = new java.util.ArrayList<>();
        for (java.math.BigDecimal m : montos(respuesta)) {
            if (permitidos.stream().noneMatch(p -> p.compareTo(m) == 0)) {
                inventados.add(m.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
            }
        }
        return inventados;
    }

    /** DEF-18: todos los montos en soles de un texto, como numeros (95 == 95.00). */
    static Set<java.math.BigDecimal> montos(String texto) {
        Set<java.math.BigDecimal> encontrados = new java.util.LinkedHashSet<>();
        if (texto == null) {
            return encontrados;
        }
        java.util.regex.Matcher m = MONTO.matcher(texto);
        while (m.find()) {
            String numero = m.group(1);
            numero = numero.matches("\\d{1,3}(,\\d{3})+(\\.\\d{1,2})?") ? numero.replace(",", "") : numero.replace(',', '.');
            encontrados.add(new java.math.BigDecimal(numero));
        }
        return encontrados;
    }

    /** DEF-19: porcentajes ("22%", "0.95 %", "4,5%"). */
    private static final java.util.regex.Pattern PORCENTAJE =
            java.util.regex.Pattern.compile("(?<![\\d.,])(\\d+(?:[.,]\\d+)?)\\s*%");

    /** DEF-19: respuesta segura cuando el modelo escribio un porcentaje que no existe en la ficha. */
    static final String DATO_NO_CONFIRMADO =
            "No pude confirmar ese dato con exactitud en la ficha técnica. Te sugiero consultarlo con un asesor de Battilana.";

    /** DEF-19: porcentajes de la respuesta que NO aparecen como numero en el texto de referencia. */
    static List<String> porcentajesInventados(String respuesta, String referencia) {
        Set<java.math.BigDecimal> permitidos = porcentajes(referencia);
        List<String> inventados = new java.util.ArrayList<>();
        for (java.math.BigDecimal p : porcentajes(respuesta)) {
            if (permitidos.stream().noneMatch(x -> x.compareTo(p) == 0)) {
                inventados.add(p.stripTrailingZeros().toPlainString() + "%");
            }
        }
        return inventados;
    }

    /** DEF-19: todos los porcentajes de un texto, como numeros (22 == 22.0; 0.95 != 95). */
    static Set<java.math.BigDecimal> porcentajes(String texto) {
        Set<java.math.BigDecimal> encontrados = new java.util.LinkedHashSet<>();
        if (texto == null) {
            return encontrados;
        }
        java.util.regex.Matcher m = PORCENTAJE.matcher(texto);
        while (m.find()) {
            encontrados.add(new java.math.BigDecimal(m.group(1).replace(',', '.')));
        }
        return encontrados;
    }

    /** DEF-18: respuesta segura cuando el modelo escribio un monto que no existe en el contexto. */
    static String respuestaSinMonto(String fuente) {
        if (normalizar(fuente == null ? "" : fuente).contains("variable")) {
            return "El precio de este producto es variable. Te sugiero consultar el precio vigente y la "
                    + "disponibilidad con un asesor de Battilana.";
        }
        return "No puedo confirmarte el precio exacto en este momento. Te sugiero consultar el precio "
                + "vigente y la disponibilidad con un asesor de Battilana.";
    }

    private static String textoDe(List<Document> documentos) {
        return documentos == null ? "" : documentos.stream()
                .map(Document::getText)
                .filter(t -> t != null)
                .collect(Collectors.joining("\n"));
    }

    /** DEF-18: lo que el bot ya respondio en esta conversacion (un monto repetido no es inventado). */
    private String historialDelBot(String convId) {
        try {
            List<org.springframework.ai.chat.messages.Message> mensajes = nutribotChatMemory.get(convId);
            if (mensajes == null) {
                return "";
            }
            return mensajes.stream()
                    .filter(m -> m.getMessageType() == org.springframework.ai.chat.messages.MessageType.ASSISTANT)
                    .map(org.springframework.ai.chat.messages.Message::getText)
                    .filter(t -> t != null)
                    .collect(Collectors.joining(" "));
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** DEF-15: respuesta cuando el cliente pide un asesor, segun el horario de atencion. */
    String mensajeAsesor(boolean asesorDisponible) {
        if (asesorDisponible) {
            return "Con gusto, puedes hablar con un asesor con el botón de abajo.";
        }
        return "Con gusto te comunico con un asesor, pero en este momento estamos fuera del horario de atención ("
                + horarioAtencion.descripcion() + "). Déjanos tus datos en el formulario y te contactaremos.";
    }

    /** Sprint 4: marca la conversacion como activa. */
    void registrarActividad(String convId, Instant cuando) {
        ultimaActividad.put(convId, cuando);
    }

    /** Sprint 4: cada 15 minutos libera las conversaciones sin mensajes en el periodo configurado. */
    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT15M")
    public void liberarConversacionesInactivas() {
        int liberadas = liberarInactivas(Instant.now(), Duration.ofMinutes(inactividadMinutos));
        if (liberadas > 0) {
            log.info("Memoria liberada de {} conversacion(es) inactiva(s).", liberadas);
        }
    }

    /**
     * Borra el tema vigente y el historial (ChatMemory) de las conversaciones inactivas.
     * Si el cliente vuelve a escribir despues, la conversacion empieza de cero, como una nueva sesion.
     * Devuelve cuantas se liberaron.
     */
    int liberarInactivas(Instant ahora, Duration inactividad) {
        Instant limite = ahora.minus(inactividad);
        int liberadas = 0;
        for (Map.Entry<String, Instant> e : ultimaActividad.entrySet()) {
            if (e.getValue().isBefore(limite) && ultimaActividad.remove(e.getKey(), e.getValue())) {
                temaPorConversacion.remove(e.getKey());
                nutribotChatMemory.clear(e.getKey());
                liberadas++;
            }
        }
        return liberadas;
    }

    /** Sprint 4: cantidad de conversaciones en memoria (para pruebas y monitoreo). */
    int conversacionesActivas() {
        return ultimaActividad.size();
    }

    /** Milisegundos transcurridos desde un instante tomado con System.nanoTime(). */
    private static long milisDesde(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }

    /**
     * Logica de atencion (sin cambios funcionales respecto del Sprint 3): decide el camino,
     * consulta la base de conocimiento y arma la respuesta.
     */
    private Resultado procesar(String message, String convId, String previousQuestion, boolean asesorDisponible) {
        String texto = normalizar(message);

        // (a) El cliente pide explicitamente un asesor -> boton directo.
        // DEF-15: el texto depende de si hay asesor; fuera de horario se piden los datos (HU09).
        if (PIDE_ASESOR.stream().anyMatch(texto::contains)) {
            return new Resultado(mensajeAsesor(asesorDisponible), "BOTON", CaminoConsulta.ASESOR);
        }

        // DEF-17: direccion, tienda o donde comprar -> respuesta oficial fija + asesor por WhatsApp
        if (PIDE_UBICACION.stream().anyMatch(texto::contains) && PIDE_MONTO.stream().noneMatch(texto::contains)) {
            return new Resultado(mensajeUbicacion(asesorDisponible), "BOTON", CaminoConsulta.UBICACION);
        }

        // HU05 (b): reformulacion -> se responde SOLO con el historial, sin recuperar nada.
        // No actualiza el tema vigente: "explicalo mas simple" no cambia de que se esta hablando.
        if (esReformulacion(texto)) {
            String reformulada = nutribotReformulacionChatClient.prompt()
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
                    .call()
                    .content();
            return new Resultado(reformulada, "NINGUNA", CaminoConsulta.REFORMULACION);
        }

        // HU05: si la pregunta es de seguimiento ("¿y cuanto cuesta?"), la contextualizamos con la
        // pregunta anterior. Asi la busqueda en Qdrant recupera los fragmentos del producto correcto
        // y se evita que el modelo mezcle informacion de otro producto.
        String consulta = message;
        String sujeto;
        if (esSeguimiento(texto)) {
            // Usamos la ultima pregunta DE TEMA de esta conversacion. Solo si no existe
            // (por ejemplo tras reiniciar el backend) caemos a la que envia el frontend.
            String tema = temaPorConversacion.get(convId);
            if (tema == null || tema.isBlank()) {
                tema = previousQuestion;
            }
            // Del tema anterior tomamos SOLO el sujeto (p. ej. "gallina ponedora"), no la
            // pregunta completa: asi la intencion la marca la pregunta actual y la busqueda
            // vectorial no se desvia hacia el tema de la consulta anterior.
            sujeto = extraerSujeto(tema);
            if (!sujeto.isBlank()) {
                // La pregunta principal va PRIMERO y el contexto al final, claramente marcado,
                // para que el modelo lo use solo para ubicar el tema y no responda de nuevo lo anterior.
                consulta = message + "\n[Contexto previo: " + sujeto + "]";
            }
        } else {
            // Pregunta nueva: pasa a ser el tema vigente de la conversacion.
            temaPorConversacion.put(convId, message);
            sujeto = extraerSujeto(message);
        }

        // (b) No hay informacion relevante -> boton directo
        List<Document> relevantes = vectorStore.similaritySearch(SearchRequest.builder()
                .query(consulta)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build());
        if (relevantes == null || relevantes.isEmpty()) {
            return new Resultado(SIN_INFORMACION, "BOTON", CaminoConsulta.SIN_INFO);
        }

        boolean consultaPrecio = ES_CONSULTA_PRECIO.stream().anyMatch(texto::contains);

        // DEF-16: verificacion de anclaje. La similitud vectorial mide si la FRASE se parece a los
        // documentos, no si el TEMA existe: "¿Que es un alimento para dragones?" obtuvo 0.723
        // (mas que preguntas reales: 0.661 y 0.617). Si ninguna palabra clave de la pregunta
        // aparece en lo recuperado, no hay de que hablar y el modelo terminaria inventando.
        if (!consultaPrecio && !temaEnBase(relevantes, message, sujeto)) {
            log.info("DEF-16: sin anclaje en la base de conocimiento para \"{}\" -> SIN_INFO", message);
            return new Resultado(SIN_INFORMACION, "BOTON", CaminoConsulta.SIN_INFO);
        }

        // DEF-18: lo que el bot ya dijo ANTES de esta respuesta (la memoria guarda la nueva al responder)
        String historialPrevio = historialDelBot(convId);
        String answer;
        String fuente;   // DEF-18: texto que se le entrego al modelo (para verificar los montos)
        if (consultaPrecio) {
            // DEF-10: primero acotamos la RECUPERACION a la lista de precios. Sin esto, una
            // consulta como "¿cuanto vale el alimento de gallinas?" llena los 6 cupos con
            // fragmentos de la ficha tecnica y del FAQ (ricos en la palabra "gallinas") y el
            // bloque de precios del producto ni siquiera llega: el filtro posterior solo podria
            // elegir el menos malo de un conjunto que ya venia equivocado.
            List<Document> deLaLista = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(consulta)
                    .topK(topK)
                    .similarityThreshold(similarityThreshold)
                    .filterExpression(new FilterExpressionBuilder().eq("source", priceDocument).build())
                    .build());
            // Si la lista de precios no esta indexada con ese nombre, seguimos con lo general.
            List<Document> base = (deLaLista == null || deLaLista.isEmpty()) ? relevantes : deLaLista;
            // DEF-16: el producto consultado debe figurar en la lista o en lo recuperado
            List<Document> recuperados = new java.util.ArrayList<>(base);
            recuperados.addAll(relevantes);
            if (!temaEnBase(recuperados, message, sujeto)) {
                log.info("DEF-16: producto sin anclaje para \"{}\" -> SIN_INFO", message);
                return new Resultado(SIN_INFORMACION, "BOTON", CaminoConsulta.SIN_INFO);
            }

            // DEF-08: ademas, NO dejamos que el modelo elija entre varios productos. Filtramos el
            // contexto al bloque del producto consultado y se lo inyectamos ya acotado, de modo
            // que le resulte imposible atribuirle el precio de otro producto.
            String contexto = contextoDelProducto(base, sujeto);
            fuente = contexto;
            answer = nutribotContextoChatClient.prompt()
                    .user("Contexto:\n" + contexto + "\n\nPregunta del cliente: " + message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
                    .call()
                    .content();
        } else if (ES_CONSULTA_FICHA.stream().anyMatch(texto::contains)) {
            // DEF-21: dato de ficha tecnica -> contexto acotado al bloque del producto consultado
            String contexto = contextoDelProducto(relevantes, sujeto);
            fuente = contexto;
            answer = nutribotContextoChatClient.prompt()
                    .user("Contexto:\n" + contexto + "\n\nPregunta del cliente: " + message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
                    .call()
                    .content();
        } else {
            // Respuesta RAG normal, recordando el historial de esta conversacion (HU05)
            // DEF-19: el QuestionAnswerAdvisor (ChatConfig) recupera los topK mas parecidos SIN umbral;
            // se replica esa busqueda para verificar los datos contra lo mismo que lee el modelo.
            List<Document> comoElAdvisor = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(consulta)
                    .topK(topK)
                    .build());
            fuente = textoDe(relevantes) + "\n" + textoDe(comoElAdvisor);
            answer = nutribotChatClient.prompt()
                    .user(consulta)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
                    .call()
                    .content();
        }
        // DEF-18: verificacion de montos. Todo "S/ n" de la respuesta debe existir en lo que se le
        // entrego al modelo o en lo que el bot ya dijo en esta conversacion; si no, es inventado
        // (el modelo llego a copiar "S/ 95.00" del ejemplo del prompt para un precio VARIABLE).
        List<String> inventados = montosInventados(answer, fuente + " " + historialPrevio);
        if (!inventados.isEmpty()) {
            log.warn("DEF-18: monto(s) {} que no figuran en el contexto para \"{}\" -> respuesta segura", inventados, message);
            return new Resultado(respuestaSinMonto(fuente), "BOTON",
                    consultaPrecio ? CaminoConsulta.PRECIO : CaminoConsulta.GENERAL);
        }
        // DEF-19: lo mismo con los porcentajes de las fichas tecnicas. El modelo llego a responder
        // "95% de proteina" tomando el 95 de "calcio 0.95%" (0.95 != 95).
        List<String> porcentajes = porcentajesInventados(answer, fuente + " " + historialPrevio);
        if (!porcentajes.isEmpty()) {
            log.warn("DEF-19: porcentaje(s) {} que no figuran en el contexto para \"{}\" -> respuesta segura", porcentajes, message);
            return new Resultado(DATO_NO_CONFIRMADO, "BOTON",
                    consultaPrecio ? CaminoConsulta.PRECIO : CaminoConsulta.GENERAL);
        }

        boolean incierto = respuestaIncierta(answer);

        if (consultaPrecio) {
            // Precio variable / sin precio / sin stock / desconocido -> boton directo.
            // Precio fijo + en stock -> preguntar si desea un asesor (boton solo si confirma).
            if (incierto) {
                return new Resultado(answer, "BOTON", CaminoConsulta.PRECIO);
            }
            return new Resultado(
                    answer + "\n\n¿Deseas que te derive con un asesor para concretar tu compra?", "PREGUNTAR",
                    CaminoConsulta.PRECIO);
        }

        // Pregunta general: si el bot no tiene la info -> boton; si respondio bien -> nada
        return new Resultado(answer, incierto ? "BOTON" : "NINGUNA", CaminoConsulta.GENERAL);
    }

    /**
     * DEF-16: ¿el TEMA de la pregunta existe en la base de conocimiento?
     *  1. Se sacan las palabras clave de la pregunta y del sujeto (en un seguimiento como
     *     "¿y cuanto cuesta?" el tema viene del sujeto), sin interrogativos, verbos ni genericas.
     *  2. Si alguna aparece (por su raiz: "ubicados" ~ "ubicada" ~ "ubicacion") en lo recuperado,
     *     el tema existe.
     *  3. Segunda oportunidad: si no aparece, se busca de nuevo SOLO con las palabras clave. Asi
     *     "¿Cual es su direccion?" encuentra el fragmento que dice "direccion" aunque la primera
     *     busqueda lo dejara en el puesto 28; "dragones" no aparece en ningun lado y se bloquea.
     * Si no queda ninguna palabra clave (saludos, "¿que alimentos tienen?") no se puede juzgar
     * y se deja responder al modelo como antes.
     */
    boolean temaEnBase(List<Document> recuperados, String pregunta, String sujeto) {
        List<String> claves = palabrasClave((pregunta == null ? "" : pregunta) + " " + (sujeto == null ? "" : sujeto));
        if (claves.isEmpty() || mencionaAlguna(recuperados, claves)) {
            return true;
        }
        List<Document> segunda = vectorStore.similaritySearch(SearchRequest.builder()
                .query(String.join(" ", claves))
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build());
        return segunda != null && mencionaAlguna(segunda, claves);
    }

    /** DEF-16: palabras (normalizadas) que dicen de que trata la pregunta. */
    static List<String> palabrasClave(String texto) {
        String limpio = normalizar(texto == null ? "" : texto).replaceAll("[^a-z0-9ñ ]", " ").trim();
        if (limpio.isEmpty()) {
            return List.of();
        }
        return java.util.Arrays.stream(limpio.split("\\s+"))
                .filter(p -> p.length() > 2)
                .filter(p -> !PALABRAS_VACIAS.contains(p) && !PALABRAS_GENERICAS.contains(p))
                .distinct()
                .toList();
    }

    /** DEF-16: ¿algun documento contiene alguna de las palabras, comparando por raiz? */
    static boolean mencionaAlguna(List<Document> documentos, List<String> palabras) {
        if (documentos == null || documentos.isEmpty()) {
            return false;
        }
        String texto = normalizar(documentos.stream()
                .map(Document::getText)
                .filter(t -> t != null)
                .collect(Collectors.joining(" ")));
        Set<String> raices = java.util.Arrays.stream(texto.split("[^a-z0-9ñ]+"))
                .filter(p -> !p.isEmpty())
                .map(ChatService::raizTema)
                .collect(Collectors.toSet());
        return palabras.stream()
                .flatMap(p -> equivalentes(p).stream())
                .anyMatch(raices::contains);
    }

    /**
     * DEF-16: sinonimos del vocabulario de la empresa. La base de conocimiento usa una palabra y el
     * cliente otra: el documento dice "UBICACION" y el cliente pregunta por la "direccion".
     * La lista es corta a proposito: solo datos institucionales que se piden con otras palabras.
     */
    private static final Map<String, List<String>> SINONIMOS = Map.of(
            "direccion", List.of("ubicacion", "ubicada", "sede", "local", "oficina"),
            "envio", List.of("entrega", "despacho", "delivery", "reparto"),
            "telefono", List.of("contacto", "celular", "whatsapp", "numero"),
            "abren", List.of("horario", "atencion"),
            "cierran", List.of("horario", "atencion"),
            "pago", List.of("pagos", "yape", "plin", "transferencia", "deposito"));

    /** DEF-16: raices con las que se busca una palabra: la suya y las de sus sinonimos. */
    static Set<String> equivalentes(String palabra) {
        String raiz = raizTema(palabra);
        Set<String> todas = new java.util.HashSet<>();
        todas.add(raiz);
        SINONIMOS.forEach((clave, lista) -> {
            if (raizTema(clave).equals(raiz)) {
                lista.forEach(sinonimo -> todas.add(raizTema(sinonimo)));
            }
        });
        return todas;
    }

    /** DEF-16: terminaciones que se quitan para comparar palabras de la misma familia. */
    private static final List<String> SUFIJOS = List.of(
            "amientos", "imientos", "amiento", "imiento", "aciones", "iciones", "ciones",
            "acion", "icion", "cion", "mente", "ados", "adas", "idos", "idas", "ores", "oras",
            "ado", "ada", "ido", "ida", "ora", "es", "s");

    /**
     * DEF-16: raiz ligera para comparar palabras de la misma familia en espanol:
     * "ubicados", "ubicada" y "ubicacion" -> "ubic"; "ponedoras" y "ponedora" -> "poned";
     * "cuyes" -> "cuy". Se quita la primera terminacion que coincida, dejando al menos 3 letras.
     * (Distinta de raiz(), que solo quita el plural y la usa contextoDelProducto.)
     */
    static String raizTema(String palabra) {
        for (String sufijo : SUFIJOS) {
            if (palabra.endsWith(sufijo) && palabra.length() - sufijo.length() >= 3) {
                return palabra.substring(0, palabra.length() - sufijo.length());
            }
        }
        return palabra;
    }

    /** DEF-16: la respuesta del modelo admite que no sabe, niega o remite a un asesor. */
    static boolean respuestaIncierta(String answer) {
        String low = (answer == null) ? "" : normalizar(answer);
        return INCERTIDUMBRE.stream().anyMatch(low::contains);
    }

    /**
     * Extrae el sujeto de una pregunta quitando signos, interrogativos, verbos, conectores y
     * sustantivos de intencion. Ejemplo:
     *   "¿Que dosis necesita una gallina ponedora?" -> "gallina ponedora"
     *   "¿Como se almacena el alimento balanceado?" -> "alimento balanceado"
     * Si no queda nada util, devuelve la pregunta original sin signos, para no perder el contexto.
     */
    static String extraerSujeto(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return "";
        }
        String limpia = normalizar(pregunta).replaceAll("[^a-z0-9ñ ]", " ").trim();
        String sujeto = java.util.Arrays.stream(limpia.split("\\s+"))
                .filter(p -> p.length() > 1)
                .filter(p -> !PALABRAS_VACIAS.contains(p))
                .collect(Collectors.joining(" "));
        return sujeto.isBlank() ? limpia : sujeto;
    }

    /**
     * DEF-08: construye el contexto de una consulta de precio conservando UNICAMENTE las lineas
     * que mencionan el producto consultado.
     *
     * La lista de precios repite el nombre del producto en cada linea ("Precio del alimento
     * balanceado para gallinas ponedoras: S/ 95.00"), por lo que el filtrado a nivel de linea es
     * preciso. Asi el modelo nunca ve los precios de los demas productos y le resulta imposible
     * cruzarlos. Si el filtro no deja ninguna linea, se devuelve el contexto completo para no
     * dejar al modelo sin informacion.
     */
    static String contextoDelProducto(List<Document> documentos, String sujeto) {
        // DEF-21: los fragmentos se unen con una LINEA EN BLANCO para que el ultimo parrafo de un
        // fragmento no se pegue al primero del siguiente (mezclaba fichas de productos distintos)
        String completo = documentos.stream()
                .map(Document::getText)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining("\n\n"));

        if (sujeto == null || sujeto.isBlank()) {
            return completo;
        }

        List<String> raices = java.util.Arrays.stream(sujeto.split("\\s+"))
                .filter(p -> p.length() > 2)
                .map(ChatService::raiz)
                .toList();
        if (raices.isEmpty()) {
            return completo;
        }

        // Puntuamos cada BLOQUE (no cada linea) por cuantas raices del sujeto contiene y
        // conservamos los de puntaje MAXIMO. Dos decisiones importantes:
        //  - Bloque y no linea: el precio, la presentacion y la disponibilidad de un producto
        //    viven en lineas distintas. Filtrando por linea, "¿que precio tiene el SACO para
        //    ponedoras?" se quedaba solo con la linea de presentacion, que no trae el precio.
        //  - Puntaje maximo y no coincidencia total: el sujeto puede arrastrar alguna palabra
        //    que no exista en el catalogo (el verbo "vale"), y exigir que esten todas haria que
        //    una sola palabra desconocida anulara el filtro en silencio (DEF-09).
        List<String> bloques = separarEnBloques(completo);
        int maximo = bloques.stream().mapToInt(b -> coincidencias(b, raices)).max().orElse(0);
        if (maximo == 0) {
            return completo;   // el sujeto no aparece en el contexto: no hay nada que filtrar
        }

        return bloques.stream()
                .filter(b -> coincidencias(b, raices) == maximo)
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * Separa el contexto en bloques tematicos. Se corta ante una linea en blanco o al inicio de
     * un nuevo "PRODUCTO:", segun como haya quedado el fragmento despues del troceado.
     */
    static List<String> separarEnBloques(String texto) {
        List<String> bloques = java.util.Arrays.stream(texto.split("(?m)\\n\\s*\\n|(?=^PRODUCTO:)"))
                .map(String::strip)
                .filter(b -> !b.isBlank())
                .toList();
        return bloques.isEmpty() ? List.of(texto) : bloques;
    }

    /** Cuenta cuantas raices del sujeto aparecen en un bloque del contexto. */
    static int coincidencias(String bloque, List<String> raices) {
        String n = normalizar(bloque);
        return (int) raices.stream().filter(n::contains).count();
    }

    /** Quita la marca de plural para que "gallinas" y "gallina" se reconozcan como lo mismo. */
    static String raiz(String palabra) {
        if (palabra.endsWith("es") && palabra.length() > 4) {
            return palabra.substring(0, palabra.length() - 2);
        }
        if (palabra.endsWith("s") && palabra.length() > 3) {
            return palabra.substring(0, palabra.length() - 1);
        }
        return palabra;
    }

    /** Detecta si el cliente pide reformular o explicar mejor la respuesta anterior. */
    static boolean esReformulacion(String textoNormalizado) {
        return MARCAS_REFORMULACION.stream().anyMatch(textoNormalizado::contains);
    }

    /** Detecta si la pregunta depende de lo dicho antes en la conversacion. */
    static boolean esSeguimiento(String textoNormalizado) {
        String t = textoNormalizado.trim();
        return t.startsWith("y ") || t.startsWith("¿y ")
                || MARCAS_SEGUIMIENTO.stream().anyMatch(t::contains);
    }

    /** Pasa a minusculas y quita tildes, para que las comparaciones no fallen por acentos. */
    static String normalizar(String s) {
        return Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }
}
