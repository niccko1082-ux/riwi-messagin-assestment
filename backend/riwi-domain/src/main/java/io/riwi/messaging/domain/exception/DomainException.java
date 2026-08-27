package io.riwi.messaging.domain.exception;

public sealed class DomainException extends RuntimeException
        permits NotFoundException, ForbiddenException, ValidationException {
    protected DomainException(String message) {
        super(message);
    }
}
