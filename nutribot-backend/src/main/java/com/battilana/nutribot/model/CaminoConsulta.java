package com.battilana.nutribot.model;

/**
 * Sprint 4: camino por el que ChatService resolvio una consulta.
 * Permite medir en el panel (HU12) como se distribuyen las consultas y donde falla el bot.
 */
public enum CaminoConsulta {
    /** El cliente pidio hablar con un asesor (HU07). */
    ASESOR,
    /** DEF-17: el cliente pregunta por la direccion, la tienda o donde comprar: respuesta fija. */
    UBICACION,
    /** "Explicalo mas simple": se responde solo con el historial, sin RAG (HU05). */
    REFORMULACION,
    /** Ningun fragmento supero el umbral de similitud: derivacion por baja certeza (HU08). */
    SIN_INFO,
    /** Consulta de precio o disponibilidad: busqueda acotada a la lista de precios (HU02). */
    PRECIO,
    /** Consulta general resuelta por RAG (HU01, HU03, HU04). */
    GENERAL,
    /** Fallo tecnico al responder (p. ej. Ollama apagado). Alimenta la metrica de fiabilidad. */
    ERROR
}
