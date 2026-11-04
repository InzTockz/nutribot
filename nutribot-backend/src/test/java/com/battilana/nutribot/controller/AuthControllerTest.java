package com.battilana.nutribot.controller;

import com.battilana.nutribot.dto.LoginRequest;
import com.battilana.nutribot.dto.SesionResponse;
import com.battilana.nutribot.model.Rol;
import com.battilana.nutribot.model.Usuario;
import com.battilana.nutribot.repository.UsuarioRepository;
import com.battilana.nutribot.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Sprint 4 (enabler de seguridad): inicio de sesion. */
@DisplayName("AuthController — inicio de sesión")
class AuthControllerTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final JwtService jwt = new JwtService("clave-de-prueba-de-al-menos-32-caracteres-123", 8);
    private final AuthController controller = new AuthController(repo, encoder, jwt);

    private Usuario usuario(boolean activo) {
        return Usuario.builder().username("asesor").passwordHash(encoder.encode("Clave-Correcta-1"))
                .nombre("Asesor de ventas").rol(Rol.ASESOR).activo(activo).build();
    }

    @Test
    void credencialesCorrectasEntreganUnTokenValido() {
        when(repo.findByUsername("asesor")).thenReturn(Optional.of(usuario(true)));

        SesionResponse s = controller.login(new LoginRequest("asesor", "Clave-Correcta-1"));

        assertThat(s.rol()).isEqualTo("ASESOR");
        assertThat(jwt.usuarioDe(s.token())).contains("asesor");
    }

    @Test
    void claveIncorrectaYUsuarioInexistenteDanElMismoError() {
        when(repo.findByUsername("asesor")).thenReturn(Optional.of(usuario(true)));
        when(repo.findByUsername("nadie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.login(new LoginRequest("asesor", "otra")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Usuario o contraseña incorrectos");
        assertThatThrownBy(() -> controller.login(new LoginRequest("nadie", "otra")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Usuario o contraseña incorrectos");
    }

    @Test
    void unUsuarioDesactivadoNoPuedeIngresar() {
        when(repo.findByUsername("asesor")).thenReturn(Optional.of(usuario(false)));
        assertThatThrownBy(() -> controller.login(new LoginRequest("asesor", "Clave-Correcta-1")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void laClaveSeGuardaComoHashBcrypt() {
        String hash = usuario(true).getPasswordHash();
        assertThat(hash).startsWith("$2a$10$").doesNotContain("Clave-Correcta-1");
    }
}
