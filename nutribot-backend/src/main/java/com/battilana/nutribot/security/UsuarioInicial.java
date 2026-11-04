package com.battilana.nutribot.security;

import com.battilana.nutribot.model.Rol;
import com.battilana.nutribot.model.Usuario;
import com.battilana.nutribot.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Sprint 4 (enabler de seguridad): al arrancar, crea el administrador (y opcionalmente un asesor)
 * con los datos de las variables de entorno, SOLO si todavia no existen.
 * Asi no queda ninguna clave por defecto escrita en el codigo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UsuarioInicial implements ApplicationRunner {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    @Value("${nutribot.security.admin-usuario:admin}")
    private String adminUsuario;
    @Value("${nutribot.security.admin-password:}")
    private String adminPassword;
    @Value("${nutribot.security.asesor-usuario:}")
    private String asesorUsuario;
    @Value("${nutribot.security.asesor-password:}")
    private String asesorPassword;

    @Override
    public void run(ApplicationArguments args) {
        crearSiNoExiste(adminUsuario, adminPassword, "Administrador", Rol.ADMIN);
        crearSiNoExiste(asesorUsuario, asesorPassword, "Asesor de ventas", Rol.ASESOR);
    }

    private void crearSiNoExiste(String usuario, String clave, String nombre, Rol rol) {
        if (usuario == null || usuario.isBlank() || usuarios.findByUsername(usuario).isPresent()) {
            return;
        }
        if (clave == null || clave.length() < 8) {
            log.warn("No se creo el usuario '{}' ({}): define una contrasena de al menos 8 caracteres en el .env.",
                    usuario, rol);
            return;
        }
        usuarios.save(Usuario.builder()
                .username(usuario)
                .passwordHash(encoder.encode(clave))
                .nombre(nombre)
                .rol(rol)
                .activo(true)
                .build());
        log.info("Usuario inicial '{}' creado con rol {}.", usuario, rol);
    }
}
