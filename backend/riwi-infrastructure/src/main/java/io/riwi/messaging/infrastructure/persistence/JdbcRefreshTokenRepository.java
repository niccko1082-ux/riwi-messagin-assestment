package io.riwi.messaging.infrastructure.persistence;

import io.riwi.messaging.domain.model.RefreshTokenRecord;
import io.riwi.messaging.domain.model.UserId;
import io.riwi.messaging.domain.port.RefreshTokenRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Sin RLS ni actor propagation a propósito (008_create_rls.sql): la rotación de refresh
 *  tokens ocurre ANTES de que exista un actor autenticado en la transacción. */
@Repository
public class JdbcRefreshTokenRepository implements RefreshTokenRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcRefreshTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public UUID store(UserId userId, String tokenHash, UUID rotatedFrom, Instant expiresAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO rw_refresh_tokens (user_id, token_hash, rotated_from, expires_at)
                VALUES (?, ?, ?, ?)
                RETURNING token_id
                """, UUID.class, userId.value(), tokenHash, rotatedFrom, Timestamp.from(expiresAt));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshTokenRecord> findByHash(String tokenHash) {
        try {
            return Optional.of(jdbcTemplate.queryForObject(
                    "SELECT * FROM rw_refresh_tokens WHERE token_hash = ?", this::mapRow, tokenHash));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public void revoke(UUID tokenId) {
        jdbcTemplate.update("UPDATE rw_refresh_tokens SET revoked_at = now() WHERE token_id = ?", tokenId);
    }

    private RefreshTokenRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp revokedAt = rs.getTimestamp("revoked_at");
        return new RefreshTokenRecord(
                rs.getObject("token_id", UUID.class),
                new UserId(rs.getObject("user_id", UUID.class)),
                rs.getString("token_hash"),
                (UUID) rs.getObject("rotated_from"),
                rs.getTimestamp("expires_at").toInstant(),
                revokedAt == null ? null : revokedAt.toInstant()
        );
    }
}
