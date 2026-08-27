package io.riwi.messaging.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.riwi.messaging.api.security.JwtAuthenticationEntryPoint;
import io.riwi.messaging.api.security.JwtAuthenticationFilter;
import io.riwi.messaging.infrastructure.security.JwtTokenParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/auth/login", "/api/auth/refresh",
            "/swagger-ui/**", "/v3/api-docs/**", "/ws/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtTokenParser tokenParser,
                                            CorsConfigurationSource corsConfigurationSource,
                                            ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API sin cookies de sesión: no hay CSRF que mitigar
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(new JwtAuthenticationEntryPoint(objectMapper)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(tokenParser), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // Mismos orígenes que el WebSocket (WebSocketConfig): el frontend corre en otro origen y
    // sin esto el preflight de CORS bloquea toda la API REST.
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${riwi.cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
