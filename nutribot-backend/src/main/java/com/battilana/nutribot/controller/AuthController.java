package com.battilana.nutribot.controller;

import com.battilana.nutribot.dto.LoginRequest;
import com.battilana.nutribot.dto.SesionResponse;
import com.battilana.nutribot.model.Usuario;
import com.battilana.nutribot.repository.UsuarioRepository;
import com.battilana.nutribot.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Sprint 4 (enabler de seguridad): inicio de sesion del personal.
 *  POST /api/auth/login   usuario + contrasena -> token (publico)
 *  GET  /api/auth/me      datos del usuario de la sesion actual (requiere token)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Mismo mensaje para "no existe" y "clave incorrecta": no se revela que usuarios existen. */
    private static final String CREDENCIALES_INVALIDAS = "Usuario o contraseña incorrectos.";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    /**
     * Hash de relleno: si el usuario no existe, igual se compara una clave contra este hash, para que
     * la respuesta tarde lo mismo y no delate por el tiempo que usuarios existen. Se genera al arrancar.
     */
    private final String hashDeRelleno;

    public AuthController(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwtService) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.hashDeRelleno = encoder.encode(java.util.UUID.randomUUID().toString());
    }

    @PostMapping("/login")
    public SesionResponse login(@RequestBody LoginRequest req) {
        String username = (req.username() == null) ? "" : req.username().trim();
        String password = (req.password() == null) ? "" : req.password();

        Usuario usuario = usuarios.findByUsername(username).orElse(null);
        boolean claveCorrecta = encoder.matches(password, usuario != null ? usuario.getPasswordHash() : hashDeRelleno);
        if (usuario == null || !claveCorrecta || !usuario.isActivo()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, CREDENCIALES_INVALIDAS);
        }
        return new SesionResponse(jwtService.generar(usuario), usuario.getUsername(), usuario.getNombre(),
                usuario.getRol().name(), jwtService.segundosDeVida());
    }

    @GetMapping("/me")
    public Map<String, String> yo(Authentication auth) {
        Usuario usuario = usuarios.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return Map.of("username", usuario.getUsername(),
                "nombre", usuario.getNombre() == null ? usuario.getUsername() : usuario.getNombre(),
                "rol", usuario.getRol().name());
    }
}
