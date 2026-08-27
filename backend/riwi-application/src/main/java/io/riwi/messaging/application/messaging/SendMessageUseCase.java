package io.riwi.messaging.application.messaging;

import io.riwi.messaging.domain.exception.ValidationException;
import io.riwi.messaging.domain.model.ChannelId;
import io.riwi.messaging.domain.model.MessageId;
import io.riwi.messaging.domain.model.UserId;
import io.riwi.messaging.domain.port.MessageRepository;

public class SendMessageUseCase {
    private final MessageRepository messageRepository;

    public SendMessageUseCase(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public MessageId execute(UserId actorId, ChannelId channelId, String content) {
        if (content == null || content.isBlank()) {
            throw new ValidationException("el contenido del mensaje no puede estar vacío");
        }
        // La membresía se valida en rw_send_message (Fase 3) — esta es defensa temprana, no
        // la autoridad final.
        return messageRepository.send(actorId, channelId, content);
    }
}
