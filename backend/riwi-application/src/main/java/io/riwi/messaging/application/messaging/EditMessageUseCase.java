package io.riwi.messaging.application.messaging;

import io.riwi.messaging.domain.exception.ValidationException;
import io.riwi.messaging.domain.model.ChannelId;
import io.riwi.messaging.domain.model.MessageId;
import io.riwi.messaging.domain.model.UserId;
import io.riwi.messaging.domain.port.MessageRepository;

public class EditMessageUseCase {
    private final MessageRepository messageRepository;

    public EditMessageUseCase(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    /** Devuelve el channelId real del mensaje editado (para el broadcast, nunca el que
     *  mande el cliente). */
    public ChannelId execute(UserId actorId, MessageId messageId, String newContent) {
        if (newContent == null || newContent.isBlank()) {
            throw new ValidationException("el contenido del mensaje no puede estar vacío");
        }
        return messageRepository.edit(actorId, messageId, newContent);
    }
}
