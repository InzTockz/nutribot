package com.battilana.nutribot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Se excluye el usuario "user" con clave aleatoria que Spring Security crea por defecto:
 * NutriBot autentica con sus propios usuarios (tabla usuario) y tokens JWT.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling   // Sprint 4: liberacion periodica de conversaciones inactivas
public class NutribotBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(NutribotBackendApplication.class, args);
	}

}
