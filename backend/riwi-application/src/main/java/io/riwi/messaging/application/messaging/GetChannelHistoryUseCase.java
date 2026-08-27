package io.riwi.messaging.application.messaging;

import io.riwi.messaging.domain.exception.ValidationException;
import io.riwi.messaging.domain.model.ChannelId;
import io.riwi.messaging.domain.model.KeysetPage;
import io.riwi.messaging.domain.model.Message;
import io.riwi.messaging.domain.model.UserId;
import io.riwi.messaging.domain.port.MessageRepository;

/** Consulta 1: historial paginado por keyset (nunca OFFSET). */
public class GetChannelHistoryUseCase {
    private static final int MAX_PAGE_SIZE = 100;

    private final MessageRepository messageRepository;

    public GetChannelHistoryUseCase(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public KeysetPage<Message> execute(UserId actorId, ChannelId channelId, Long cursor, int limit) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new ValidationException("limit debe estar entre 1 y " + MAX_PAGE_SIZE);
        }
        return messageRepository.history(actorId, channelId, cursor, limit);
    }
}
