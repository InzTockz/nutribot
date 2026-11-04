package com.battilana.nutribot.security;

import com.battilana.nutribot.model.Rol;
import com.battilana.nutribot.model.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sprint 4 (enabler de seguridad): emision y validacion de tokens. */
@DisplayName("JwtService — tokens de sesión")
class JwtServiceTest {

    private static final String SECRETO = "clave-de-prueba-de-al-menos-32-caracteres-123";
    private final Usuario admin = Usuario.builder().username("admin").rol(Rol.ADMIN).activo(true).build();

    @Test
    void unTokenRecienEmitidoEsValido() {
        JwtService jwt = new JwtService(SECRETO, 8);
        assertThat(jwt.usuarioDe(jwt.generar(admin))).contains("admin");
        assertThat(jwt.segundosDeVida()).isEqualTo(8 * 3600);
    }

    @Test
    void unTokenModificadoSeRechaza() {
        JwtService jwt = new JwtService(SECRETO, 8);
        String token = jwt.generar(admin);
        String alterado = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
        assertThat(jwt.usuarioDe(alterado)).isEmpty();
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechaza() {
        String ajeno = new JwtService("otra-clave-distinta-de-al-menos-32-caracteres", 8).generar(admin);
        assertThat(new JwtService(SECRETO, 8).usuarioDe(ajeno)).isEmpty();
    }

    @Test
    void textoQueNoEsUnTokenSeRechaza() {
        JwtService jwt = new JwtService(SECRETO, 8);
        assertThat(jwt.usuarioDe("no-es-un-token")).isEmpty();
        assertThat(jwt.usuarioDe("")).isEmpty();
    }

    @Test
    void unTokenVencidoSeRechaza() throws InterruptedException {
        JwtService jwt = new JwtService(SECRETO, 0);   // vence en el mismo instante en que se emite
        String token = jwt.generar(admin);
        Thread.sleep(1100);                            // el vencimiento del JWT tiene precision de segundos
        assertThat(jwt.usuarioDe(token)).isEmpty();
    }

    @Test
    void unSecretoCortoImpideArrancar() {
        assertThatThrownBy(() -> new JwtService("corta", 8))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 caracteres");
    }
}
