package io.riwi.messaging.application.auth;

import io.riwi.messaging.domain.exception.ForbiddenException;
import io.riwi.messaging.domain.model.*;
import io.riwi.messaging.domain.port.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Rotación: cada refresh token sirve una sola vez. Un intento de reutilizar uno ya
 *  rotado/revocado es la señal clásica de robo de token — se revoca aquí, no se reintenta. */
public class RefreshTokenUseCase {
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final TokenHasher tokenHasher;
    private final AccessTokenIssuer accessTokenIssuer;
    private final Duration refreshTokenTtl;
    private final Clock clock;

    public RefreshTokenUseCase(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository,
                                TokenHasher tokenHasher, AccessTokenIssuer accessTokenIssuer,
                                Duration refreshTokenTtl, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.tokenHasher = tokenHasher;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenTtl = refreshTokenTtl;
        this.clock = clock;
    }

    public AuthSession execute(String rawRefreshToken) {
        String hash = tokenHasher.hash(rawRefreshToken);
        RefreshTokenRecord record = refreshTokenRepository.findByHash(hash)
                .orElseThrow(() -> new ForbiddenException("refresh token inválido"));

        if (!record.isUsable(Instant.now(clock))) {
            throw new ForbiddenException("refresh token inválido o ya usado");
        }

        User user = userRepository.findById(record.userId())
                .filter(User::active)
                .orElseThrow(() -> new ForbiddenException("usuario inactivo"));

        refreshTokenRepository.revoke(record.tokenId());

        String newRawRefreshToken = RefreshTokenGenerator.generate();
        Instant newExpiresAt = Instant.now(clock).plus(refreshTokenTtl);
        refreshTokenRepository.store(user.id(), tokenHasher.hash(newRawRefreshToken), record.tokenId(), newExpiresAt);

        AccessToken accessToken = accessTokenIssuer.issue(user);

        return new AuthSession(
                accessToken.value(), accessToken.expiresAt(),
                newRawRefreshToken, newExpiresAt,
                user.id()
        );
    }
}
