package io.riwi.messaging.infrastructure.persistence;

import io.riwi.messaging.domain.model.*;
import io.riwi.messaging.domain.port.MessageRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcMessageRepository implements MessageRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public MessageId send(UserId actorId, ChannelId channelId, String content) {
        try {
            ActorPropagation.setActor(jdbcTemplate, actorId);
            Long id = jdbcTemplate.queryForObject(
                    "SELECT rw_send_message(?::uuid, ?)", Long.class, channelId.value(), content);
            return new MessageId(id);
        } catch (DataAccessException e) {
            throw PgErrorMapper.translate(e);
        }
    }

    @Override
    @Transactional
    public ChannelId edit(UserId actorId, MessageId messageId, String newContent) {
        try {
            ActorPropagation.setActor(jdbcTemplate, actorId);
            callVoid("SELECT rw_edit_message(?, ?)", messageId.value(), newContent);
            return fetchChannelId(messageId);
        } catch (DataAccessException e) {
            throw PgErrorMapper.translate(e);
        }
    }

    @Override
    @Transactional
    public ChannelId delete(UserId actorId, MessageId messageId) {
        try {
            ActorPropagation.setActor(jdbcTemplate, actorId);
            callVoid("SELECT rw_delete_message(?)", messageId.value());
            return fetchChannelId(messageId);
        } catch (DataAccessException e) {
            throw PgErrorMapper.translate(e);
        }
    }

    // channel_id real del mensaje, nunca el que mande el cliente (hallazgo de seguridad:
    // spoofing del topic de broadcast por WebSocket vía un channelId falso en la petición).
    private ChannelId fetchChannelId(MessageId messageId) {
        UUID channelId = jdbcTemplate.queryForObject(
                "SELECT channel_id FROM rw_messages WHERE message_id = ?", UUID.class, messageId.value());
        return new ChannelId(channelId);
    }

    // Consulta 1: keyset sobre message_id (BIGINT IDENTITY monótono, ver docs/data-model.md
    // §3). RLS filtra por membresía — sin ella, este SELECT devolvería mensajes de canales
    // ajenos. Se pide limit+1 para saber si hay página siguiente sin un segundo roundtrip.
    @Override
    @Transactional(readOnly = true)
    public KeysetPage<Message> history(UserId actorId, ChannelId channelId, Long cursor, int limit) {
        ActorPropagation.setActor(jdbcTemplate, actorId);
        // ?::bigint (no ? a secas): un cursor null no le da a Postgres contexto suficiente
        // para inferir el tipo del parámetro en "? IS NULL" -> "could not determine data
        // type of parameter" (BadSqlGrammarException).
        String sql = """
                SELECT message_id, channel_id, sender_id, content, status, edited_at, created_at
                  FROM rw_messages
                 WHERE channel_id = ?
                   AND (?::bigint IS NULL OR message_id < ?::bigint)
                 ORDER BY message_id DESC
                 LIMIT ?
                """;
        List<Message> rows = jdbcTemplate.query(sql, this::mapMessage,
                channelId.value(), cursor, cursor, limit + 1);
        return toPage(rows, limit, Message::id);
    }

    // Consulta 2: ts_headline resalta el término encontrado (requisito explícito). Mismo
    // esquema de keyset que el historial.
    @Override
    @Transactional(readOnly = true)
    public KeysetPage<MessageSearchResult> search(UserId actorId, String term, Long cursor, int limit) {
        ActorPropagation.setActor(jdbcTemplate, actorId);
        String sql = """
                SELECT message_id, channel_id,
                       ts_headline('spanish', content, plainto_tsquery('spanish', ?),
                                   'StartSel=<mark>,StopSel=</mark>') AS highlighted,
                       ts_rank(search_vector, plainto_tsquery('spanish', ?)) AS rank
                  FROM rw_messages
                 WHERE search_vector @@ plainto_tsquery('spanish', ?)
                   AND is_deleted = FALSE
                   AND (?::bigint IS NULL OR message_id < ?::bigint)
                 ORDER BY message_id DESC
                 LIMIT ?
                """;
        List<MessageSearchResult> rows = jdbcTemplate.query(sql, this::mapSearchResult,
                term, term, term, cursor, cursor, limit + 1);
        return toPage(rows, limit, MessageSearchResult::id);
    }

    private <T> KeysetPage<T> toPage(List<T> rows, int limit, java.util.function.Function<T, MessageId> idExtractor) {
        boolean hasMore = rows.size() > limit;
        List<T> page = hasMore ? rows.subList(0, limit) : rows;
        Long nextCursor = hasMore ? idExtractor.apply(page.get(page.size() - 1)).value() : null;
        return new KeysetPage<>(new ArrayList<>(page), nextCursor);
    }

    private void callVoid(String sql, Object... args) {
        jdbcTemplate.query(sql, (ResultSetExtractor<Void>) rs -> null, args);
    }

    private Message mapMessage(ResultSet rs, int rowNum) throws SQLException {
        java.sql.Timestamp editedAt = rs.getTimestamp("edited_at");
        return new Message(
                new MessageId(rs.getLong("message_id")),
                new ChannelId(rs.getObject("channel_id", UUID.class)),
                new UserId(rs.getObject("sender_id", UUID.class)),
                rs.getString("content"),
                MessageStatus.fromDb(rs.getString("status")),
                editedAt == null ? null : editedAt.toInstant(),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private MessageSearchResult mapSearchResult(ResultSet rs, int rowNum) throws SQLException {
        return new MessageSearchResult(
                new MessageId(rs.getLong("message_id")),
                new ChannelId(rs.getObject("channel_id", UUID.class)),
                rs.getString("highlighted"),
                rs.getDouble("rank")
        );
    }
}
