package com.battilana.nutribot.security;

import com.battilana.nutribot.repository.UsuarioRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Sprint 4 (enabler de seguridad): quien puede llamar a cada parte de la API.
 *
 *   /api/public/**                       cualquiera (el cliente en el chat)
 *   /api/auth/login                      cualquiera (pantalla de inicio de sesion)
 *   /api/admin/pending-queries/**        ADMIN o ASESOR
 *   /api/admin/derivaciones/**           ADMIN o ASESOR
 *   /api/admin/**  (el resto)            solo ADMIN
 *   cualquier otra ruta                  denegada
 *
 * Sin token valido -> 401. Con token pero sin el rol necesario -> 403.
 * Sin sesiones en el servidor (STATELESS): cada peticion trae su token.
 * CSRF desactivado porque la API no usa cookies de sesion: el token viaja en una cabecera.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService,
                                           UsuarioRepository usuarios) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())          // usa las reglas de CorsConfig
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()   // preflight de CORS
                        .requestMatchers("/error").permitAll()                    // mensajes de error (400, 404...)
                        .requestMatchers("/api/public/**", "/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/**").authenticated()
                        .requestMatchers("/api/admin/pending-queries/**", "/api/admin/derivaciones/**")
                            .hasAnyRole("ADMIN", "ASESOR")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new JwtAuthFilter(jwtService, usuarios), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** BCrypt: hash lento y con "sal" aleatoria; dos claves iguales producen hashes distintos. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
