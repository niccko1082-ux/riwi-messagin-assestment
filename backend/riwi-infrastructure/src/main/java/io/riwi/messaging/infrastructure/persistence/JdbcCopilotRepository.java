package io.riwi.messaging.infrastructure.persistence;

import com.pgvector.PGvector;
import io.riwi.messaging.domain.model.*;
import io.riwi.messaging.domain.port.CopilotRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcCopilotRepository implements CopilotRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcCopilotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Consulta 3: rw_copilot_search_context ya filtra por canales del actor vía
    // rw_my_channel_ids() (SECURITY DEFINER) — no se duplica esa lógica aquí.
    @Override
    @Transactional(readOnly = true)
    public List<ContextChunk> searchContext(UserId actorId, float[] queryEmbedding, int matchCount) {
        ActorPropagation.setActor(jdbcTemplate, actorId);
        String sql = "SELECT message_id, channel_id, content, similarity " +
                "FROM rw_copilot_search_context(?, ?)";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ContextChunk(
                new MessageId(rs.getLong("message_id")),
                new ChannelId(rs.getObject("channel_id", UUID.class)),
                rs.getString("content"),
                rs.getDouble("similarity")
        ), new PGvector(queryEmbedding), matchCount);
    }

    @Override
    @Transactional
    public long logQuery(UserId actorId, String question, String answer, Integer tokensUsed,
                          boolean hadSufficientContext, String systemPromptVersion, List<Citation> citations) {
        ActorPropagation.setActor(jdbcTemplate, actorId);
        String sql = "SELECT rw_log_copilot_query(?, ?, ?, ?, ?, ?, ?)";
        Long[] citedIds = citations.stream().map(c -> c.messageId().value()).toArray(Long[]::new);
        BigDecimal[] scores = citations.stream()
                .map(c -> BigDecimal.valueOf(c.similarityScore())).toArray(BigDecimal[]::new);
        // Arrays bigint[]/numeric[] no los arma JdbcTemplate solo: requieren
        // Connection.createArrayOf, de ahí el ConnectionCallback.
        return jdbcTemplate.execute((Connection con) -> {
            Array citedArray = con.createArrayOf("bigint", citedIds);
            Array scoreArray = con.createArrayOf("numeric", scores);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, question);
                ps.setString(2, answer);
                if (tokensUsed == null) {
                    ps.setNull(3, Types.INTEGER);
                } else {
                    ps.setInt(3, tokensUsed);
                }
                ps.setBoolean(4, hadSufficientContext);
                ps.setString(5, systemPromptVersion);
                ps.setArray(6, citedArray);
                ps.setArray(7, scoreArray);
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        });
    }

    // Consulta 4: uso acumulado por usuario, agregando rw_copilot_queries.
    @Override
    @Transactional(readOnly = true)
    public CopilotUsageSummary getUsageSummary(UserId actorId) {
        ActorPropagation.setActor(jdbcTemplate, actorId);
        String sql = """
                SELECT count(*) AS total_queries,
                       coalesce(sum(tokens_used), 0) AS total_tokens,
                       max(created_at) AS last_query_at
                  FROM rw_copilot_queries
                 WHERE user_id = ?
                """;
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            Timestamp lastQueryAt = rs.getTimestamp("last_query_at");
            return new CopilotUsageSummary(rs.getLong("total_queries"), rs.getLong("total_tokens"),
                    lastQueryAt == null ? null : lastQueryAt.toInstant());
        }, actorId.value());
    }
}
