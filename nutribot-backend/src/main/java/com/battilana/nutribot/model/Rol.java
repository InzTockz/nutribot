package com.battilana.nutribot.model;

/**
 * Sprint 4 (enabler de seguridad): roles del personal de Battilana.
 *  - ADMIN:  todo el panel (base de conocimiento, metricas, revision, consultas, derivaciones).
 *  - ASESOR: solo consultas pendientes y derivaciones (su trabajo del dia).
 * El cliente no tiene rol: usa el chat sin iniciar sesion.
 */
public enum Rol {
    ADMIN,
    ASESOR
}
