package io.riwi.messaging.api.config;

import io.riwi.messaging.api.security.JwtAuthenticationFilter;
import io.riwi.messaging.infrastructure.security.JwtTokenParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/auth/login", "/api/auth/refresh",
            "/swagger-ui/**", "/v3/api-docs/**", "/ws/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtTokenParser tokenParser) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API sin cookies de sesión: no hay CSRF que mitigar
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(tokenParser), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
