package io.riwi.messaging.application.messaging;

import io.riwi.messaging.domain.model.ChannelId;
import io.riwi.messaging.domain.model.MessageId;
import io.riwi.messaging.domain.model.UserId;
import io.riwi.messaging.domain.port.MessageRepository;

public class DeleteMessageUseCase {
    private final MessageRepository messageRepository;

    public DeleteMessageUseCase(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public ChannelId execute(UserId actorId, MessageId messageId) {
        return messageRepository.delete(actorId, messageId);
    }
}
