package io.riwi.messaging.infrastructure.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.riwi.messaging.domain.model.AccessToken;
import io.riwi.messaging.domain.model.User;
import io.riwi.messaging.domain.port.AccessTokenIssuer;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/** Claims incluyen nombre y cargo: el copiloto (Fase 5) construye su contexto de usuario en
 *  el servidor a partir del token, sin volver a consultar la BD por esos datos. */
@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {
    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtAccessTokenIssuer(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes());
    }

    @Override
    public AccessToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        String token = Jwts.builder()
                .subject(user.id().value().toString())
                .claim("email", user.email())
                .claim("name", user.fullName())
                .claim("job_title", user.jobTitle())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new AccessToken(token, expiresAt);
    }
}
