package io.riwi.messaging.domain.exception;

/** Mapea SQLSTATE 42501 (insufficient_privilege) desde las funciones de la Fase 3. */
public final class ForbiddenException extends DomainException {
    public ForbiddenException(String message) {
        super(message);
    }
}
