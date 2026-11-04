package com.battilana.nutribot.security;

import com.battilana.nutribot.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Sprint 4 (enabler de seguridad): lee la cabecera "Authorization: Bearer <token>", valida el
 * token y, si es correcto, registra al usuario y su rol para esta peticion.
 *
 * El rol y el estado se leen de la base en cada peticion (no del token): si un administrador
 * desactiva a un usuario, su sesion deja de funcionar de inmediato aunque el token no haya vencido.
 *
 * No es un @Component a proposito: si lo fuera, Spring tambien lo registraria como filtro
 * normal del servidor y se ejecutaria fuera de la cadena de seguridad. Se crea en SecurityConfig.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarios;

    public JwtAuthFilter(JwtService jwtService, UsuarioRepository usuarios) {
        this.jwtService = jwtService;
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cabecera = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecera != null && cabecera.startsWith(PREFIJO)) {
            jwtService.usuarioDe(cabecera.substring(PREFIJO.length()))
                    .flatMap(usuarios::findByUsername)
                    .filter(u -> u.isActivo())
                    .ifPresent(u -> SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    u.getUsername(), null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + u.getRol().name())))));
        }
        chain.doFilter(request, response);
    }
}
