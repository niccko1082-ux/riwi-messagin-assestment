package io.riwi.messaging.api.web.dto;

import io.riwi.messaging.domain.model.ConversationSummary;

import java.time.Instant;
import java.util.UUID;

public record ConversationSummaryResponse(
        UUID channelId, String name, String channelType,
        Long lastMessageId, String lastMessageContent, UUID lastMessageSenderId,
        Instant lastMessageAt, long unreadCount
) {
    public static ConversationSummaryResponse from(ConversationSummary c) {
        return new ConversationSummaryResponse(
                c.channelId().value(), c.name(), c.channelType().name().toLowerCase(),
                c.lastMessageId() == null ? null : c.lastMessageId().value(),
                c.lastMessageContent(),
                c.lastMessageSenderId() == null ? null : c.lastMessageSenderId().value(),
                c.lastMessageAt(), c.unreadCount());
    }
}
