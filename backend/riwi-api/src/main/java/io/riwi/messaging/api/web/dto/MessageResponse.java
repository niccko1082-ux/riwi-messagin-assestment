package io.riwi.messaging.api.web.dto;

import io.riwi.messaging.domain.model.Message;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        long id, UUID channelId, UUID senderId, String content, String status,
        Instant editedAt, Instant createdAt
) {
    public static MessageResponse from(Message m) {
        return new MessageResponse(
                m.id().value(), m.channelId().value(), m.senderId().value(), m.content(),
                m.status().name().toLowerCase(), m.editedAt(), m.createdAt());
    }
}
