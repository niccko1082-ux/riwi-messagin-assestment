package io.riwi.messaging.domain.exception;

/** Falla del proveedor de IA externo (NVIDIA NIM u otro compatible OpenAI). */
public final class AiProviderException extends DomainException {
    public AiProviderException(String message) {
        super(message);
    }
}
