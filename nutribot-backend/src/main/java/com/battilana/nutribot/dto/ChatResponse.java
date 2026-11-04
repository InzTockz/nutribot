package com.battilana.nutribot.dto;

/**
 * Respuesta de NutriBot.
 *  - accion: NINGUNA | BOTON (mostrar asesor) | PREGUNTAR (ofrecer y esperar confirmacion).
 *  - interaccionId (Sprint 4): id de la fila en la tabla interaccion; el frontend lo usa para
 *    que el cliente valore la respuesta (HU13). Puede ser null si no se pudo registrar.
 */
public record ChatResponse(String answer, String accion, Long interaccionId) {
}
