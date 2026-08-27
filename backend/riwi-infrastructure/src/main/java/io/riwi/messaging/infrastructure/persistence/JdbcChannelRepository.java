package io.riwi.messaging.infrastructure.persistence;

import io.riwi.messaging.domain.model.*;
import io.riwi.messaging.domain.port.ChannelRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcChannelRepository implements ChannelRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcChannelRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationSummary> listConversations(UserId actorId) {
        ActorPropagation.setActor(jdbcTemplate, actorId);
        return jdbcTemplate.query("SELECT * FROM rw_user_conversations", this::mapRow);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMember(UserId actorId, ChannelId channelId) {
        ActorPropagation.setActor(jdbcTemplate, actorId);
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM rw_channel_members WHERE channel_id = ? AND user_id = ?)",
                Boolean.class, channelId.value(), actorId.value());
        return Boolean.TRUE.equals(exists);
    }

    private ConversationSummary mapRow(ResultSet rs, int rowNum) throws SQLException {
        // wasNull() refleja la última columna leída con cualquier getXXX(): debe capturarse
        // en el mismo statement que getLong("last_message_id"), nunca como argumento vivo
        // del constructor de más abajo (ahí ya habría leído otra columna de por medio y
        // reflejaría la nulidad equivocada).
        long lastMessageIdRaw = rs.getLong("last_message_id");
        MessageId lastMessageId = rs.wasNull() ? null : new MessageId(lastMessageIdRaw);
        Timestamp lastMessageAt = rs.getTimestamp("last_message_at");
        Object senderIdRaw = rs.getObject("last_message_sender_id");
        return new ConversationSummary(
                new ChannelId(rs.getObject("channel_id", UUID.class)),
                rs.getString("name"),
                ChannelType.fromDb(rs.getString("channel_type")),
                lastMessageId,
                rs.getString("last_message_content"),
                senderIdRaw == null ? null : new UserId((UUID) senderIdRaw),
                lastMessageAt == null ? null : lastMessageAt.toInstant(),
                rs.getLong("unread_count")
        );
    }
}
