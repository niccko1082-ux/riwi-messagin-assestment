package io.riwi.messaging.domain.port;

import io.riwi.messaging.domain.model.RefreshTokenRecord;
import io.riwi.messaging.domain.model.UserId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {
    UUID store(UserId userId, String tokenHash, UUID rotatedFrom, Instant expiresAt);

    Optional<RefreshTokenRecord> findByHash(String tokenHash);

    void revoke(UUID tokenId);
}
