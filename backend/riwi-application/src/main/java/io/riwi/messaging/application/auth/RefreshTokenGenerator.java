package io.riwi.messaging.application.auth;

import java.security.SecureRandom;
import java.util.Base64;

/** Utilidad técnica (no una regla de negocio intercambiable): valor opaco aleatorio para el
 *  refresh token, antes de hashearlo con TokenHasher para guardarlo. */
final class RefreshTokenGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    private RefreshTokenGenerator() {
    }

    static String generate() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
