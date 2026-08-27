package io.riwi.messaging.application.messaging;

import io.riwi.messaging.domain.model.ConversationSummary;
import io.riwi.messaging.domain.model.UserId;
import io.riwi.messaging.domain.port.ChannelRepository;

import java.util.List;

public class ListConversationsUseCase {
    private final ChannelRepository channelRepository;

    public ListConversationsUseCase(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    public List<ConversationSummary> execute(UserId actorId) {
        return channelRepository.listConversations(actorId);
    }
}
