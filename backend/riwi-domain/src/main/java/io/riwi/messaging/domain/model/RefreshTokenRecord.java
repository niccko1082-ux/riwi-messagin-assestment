package io.riwi.messaging.domain.model;

import java.time.Instant;
import java.util.UUID;

public record RefreshTokenRecord(
        UUID tokenId,
        UserId userId,
        String tokenHash,
        UUID rotatedFrom,
        Instant expiresAt,
        Instant revokedAt
) {
    public boolean isUsable(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
