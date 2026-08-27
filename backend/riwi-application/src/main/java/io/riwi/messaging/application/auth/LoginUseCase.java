package io.riwi.messaging.application.auth;

import io.riwi.messaging.domain.exception.ValidationException;
import io.riwi.messaging.domain.model.AccessToken;
import io.riwi.messaging.domain.model.User;
import io.riwi.messaging.domain.port.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class LoginUseCase {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHasher tokenHasher;
    private final Duration refreshTokenTtl;
    private final Clock clock;

    public LoginUseCase(UserRepository userRepository, PasswordHasher passwordHasher,
                         AccessTokenIssuer accessTokenIssuer, RefreshTokenRepository refreshTokenRepository,
                         TokenHasher tokenHasher, Duration refreshTokenTtl, Clock clock) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenHasher = tokenHasher;
        this.refreshTokenTtl = refreshTokenTtl;
        this.clock = clock;
    }

    public AuthSession execute(String email, String rawPassword) {
        // Mensaje de error genérico a propósito: no revela si el problema fue el email o la
        // contraseña (evita enumeración de cuentas).
        User user = userRepository.findByEmail(email)
                .filter(User::active)
                .orElseThrow(() -> new ValidationException("credenciales inválidas"));

        if (!passwordHasher.matches(rawPassword, user.passwordHash())) {
            throw new ValidationException("credenciales inválidas");
        }

        AccessToken accessToken = accessTokenIssuer.issue(user);

        String rawRefreshToken = RefreshTokenGenerator.generate();
        Instant refreshExpiresAt = Instant.now(clock).plus(refreshTokenTtl);
        refreshTokenRepository.store(user.id(), tokenHasher.hash(rawRefreshToken), null, refreshExpiresAt);

        return new AuthSession(
                accessToken.value(), accessToken.expiresAt(),
                rawRefreshToken, refreshExpiresAt,
                user.id()
        );
    }
}
