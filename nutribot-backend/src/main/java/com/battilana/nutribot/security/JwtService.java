package com.battilana.nutribot.security;

import com.battilana.nutribot.model.Usuario;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Sprint 4 (enabler de seguridad): emite y valida los tokens JWT de sesion.
 *
 * El token es la "pulsera" del personal: se entrega una vez al iniciar sesion y se muestra
 * en cada peticion. Va firmado con HMAC-SHA256; si alguien lo modifica, la firma deja de
 * coincidir y el token se rechaza.
 */
@Service
public class JwtService {

    private final SecretKey clave;
    private final Duration duracion;

    public JwtService(@Value("${nutribot.security.jwt-secret}") String secreto,
                      @Value("${nutribot.security.jwt-horas:8}") long horas) {
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "NUTRIBOT_JWT_SECRET debe tener al menos 32 caracteres (definelo en el archivo .env).");
        }
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.duracion = Duration.ofHours(horas);
    }

    /** Genera el token de un usuario: incluye su usuario (subject), su rol y su vencimiento. */
    public String generar(Usuario usuario) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(usuario.getUsername())
                .claim("rol", usuario.getRol().name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(duracion)))
                .signWith(clave)
                .compact();
    }

    /** Devuelve el usuario del token si la firma es valida y no vencio; vacio en cualquier otro caso. */
    public Optional<String> usuarioDe(String token) {
        try {
            return Optional.ofNullable(Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long segundosDeVida() {
        return duracion.toSeconds();
    }
}
