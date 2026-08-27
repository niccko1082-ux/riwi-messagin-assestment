package io.riwi.messaging.domain.port;

/** Hash determinístico (no BCrypt) para permitir SELECT ... WHERE token_hash = ?. Distinto
 *  de PasswordHasher, que usa un algoritmo con salto por diseño. */
public interface TokenHasher {
    String hash(String rawToken);
}
