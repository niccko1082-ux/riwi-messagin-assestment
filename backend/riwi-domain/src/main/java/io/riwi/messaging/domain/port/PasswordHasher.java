package io.riwi.messaging.domain.port;

/** DIP: el dominio define el contrato de hashing; la implementación (BCrypt, infra) es
 *  intercambiable sin tocar casos de uso. */
public interface PasswordHasher {
    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
