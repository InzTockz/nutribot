package com.battilana.nutribot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Origenes (sitios web) que pueden llamar a la API desde el navegador.
 * Sprint 4: la lista se configura en nutribot.cors.allowed-origins (variable NUTRIBOT_CORS_ORIGINS),
 * separada por comas: el panel de Angular y, en produccion, la web de Battilana donde va el chat.
 * Spring Security reutiliza estas reglas (cors(Customizer.withDefaults())).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${nutribot.cors.allowed-origins:http://localhost:4200}")
    private String[] origenesPermitidos;

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origenesPermitidos)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")                        // incluye Authorization (token)
                .exposedHeaders("Content-Disposition");     // nombre de los archivos Excel
    }
}
