package io.riwi.messaging.domain.port;

import io.riwi.messaging.domain.model.ConversationSummary;
import io.riwi.messaging.domain.model.UserId;

import java.util.List;

public interface ChannelRepository {
    /** SELECT rw_user_conversations (vista, Fase 3), con el actor fijado en la transacción. */
    List<ConversationSummary> listConversations(UserId actorId);
}
