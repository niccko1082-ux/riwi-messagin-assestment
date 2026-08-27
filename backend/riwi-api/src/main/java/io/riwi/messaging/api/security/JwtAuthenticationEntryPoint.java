package io.riwi.messaging.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.riwi.messaging.api.web.ApiErrorResponse;
import io.riwi.messaging.api.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.time.Instant;

/** Sin esto, Spring Security responde 403 por defecto ante un JWT ausente/inválido/expirado
 *  (reservado para autorización, no autenticación) — el frontend solo dispara su flujo de
 *  refresh de token ante 401, así que nunca se disparaba y la sesión quedaba atascada. */
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws java.io.IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(), HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "credenciales ausentes o inválidas", MDC.get(CorrelationIdFilter.MDC_KEY), request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
