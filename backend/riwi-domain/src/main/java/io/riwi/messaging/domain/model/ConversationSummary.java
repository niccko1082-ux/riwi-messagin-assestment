package io.riwi.messaging.domain.model;

import java.time.Instant;

/** Proyección de la vista rw_user_conversations (Fase 3). */
public record ConversationSummary(
        ChannelId channelId,
        String name,
        ChannelType channelType,
        MessageId lastMessageId,
        String lastMessageContent,
        UserId lastMessageSenderId,
        Instant lastMessageAt,
        long unreadCount
) {
}
