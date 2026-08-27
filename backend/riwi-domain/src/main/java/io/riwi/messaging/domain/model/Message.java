package io.riwi.messaging.domain.model;

import java.time.Instant;

public record Message(
        MessageId id,
        ChannelId channelId,
        UserId senderId,
        String content,
        MessageStatus status,
        Instant editedAt,
        Instant createdAt
) {
}
