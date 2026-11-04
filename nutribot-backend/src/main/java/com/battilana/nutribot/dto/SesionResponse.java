package com.battilana.nutribot.dto;

/**
 * Sprint 4: datos de la sesion. El frontend guarda el token y lo envia en cada peticion
 * al panel (cabecera Authorization: Bearer ...). Nunca se devuelve la contrasena ni su hash.
 */
public record SesionResponse(String token, String username, String nombre, String rol, long expiraEnSegundos) {
}
