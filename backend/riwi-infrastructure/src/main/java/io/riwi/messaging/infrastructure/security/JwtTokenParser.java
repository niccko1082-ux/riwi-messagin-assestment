package io.riwi.messaging.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Optional;
import java.util.UUID;

/** Único punto de decodificación de JWT: el filtro de autenticación (api) nunca lee el token
 *  directamente, siempre pasa por aquí. */
@Component
public class JwtTokenParser {
    private final SecretKey signingKey;

    public JwtTokenParser(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes());
    }

    public Optional<AuthenticatedClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.of(new AuthenticatedClaims(
                    UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("name", String.class),
                    claims.get("job_title", String.class)
            ));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record AuthenticatedClaims(UUID userId, String email, String name, String jobTitle) {
    }
}
