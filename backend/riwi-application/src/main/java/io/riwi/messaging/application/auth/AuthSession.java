package io.riwi.messaging.application.auth;

import io.riwi.messaging.domain.model.UserId;

import java.time.Instant;

public record AuthSession(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        UserId userId
) {
}
