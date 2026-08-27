package io.riwi.messaging.api.web;

import io.riwi.messaging.domain.exception.AiProviderException;
import io.riwi.messaging.domain.exception.ForbiddenException;
import io.riwi.messaging.domain.exception.NotFoundException;
import io.riwi.messaging.domain.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/** Manejo uniforme de errores (requisito explícito): toda excepción de dominio se traduce a
 *  un código HTTP correcto y un cuerpo consistente con el correlation id de la petición. Los
 *  errores no mapeados nunca exponen el mensaje interno (evita filtrar detalles de
 *  implementación/stack) — se registran en el log con el correlation id para diagnóstico. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(ValidationException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // El mensaje real (puede traer detalles de RestClientException: URLs, causas de red) solo
    // va al log, nunca al cliente — a diferencia de los otros handlers, este envuelve fallas
    // externas (NVIDIA NIM), no errores de negocio con mensaje seguro para mostrar.
    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ApiErrorResponse> handleAiProvider(AiProviderException ex, HttpServletRequest request) {
        log.warn("proveedor de IA no disponible [correlationId={}]: {}", MDC.get(CorrelationIdFilter.MDC_KEY), ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "el copiloto de IA no está disponible en este momento", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex,
                                                                    HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("solicitud inválida");
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("error no controlado [correlationId={}]", MDC.get(CorrelationIdFilter.MDC_KEY), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "error interno del servidor", request);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message,
                MDC.get(CorrelationIdFilter.MDC_KEY), request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
