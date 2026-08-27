package io.riwi.messaging.api.web.dto;

import io.riwi.messaging.application.auth.AuthSession;

import java.time.Instant;

public record AuthResponse(
        String accessToken, Instant accessTokenExpiresAt,
        String refreshToken, Instant refreshTokenExpiresAt
) {
    public static AuthResponse from(AuthSession session) {
        return new AuthResponse(session.accessToken(), session.accessTokenExpiresAt(),
                session.refreshToken(), session.refreshTokenExpiresAt());
    }
}
