package io.riwi.messaging.application.messaging;

import io.riwi.messaging.domain.exception.ValidationException;
import io.riwi.messaging.domain.model.KeysetPage;
import io.riwi.messaging.domain.model.MessageSearchResult;
import io.riwi.messaging.domain.model.UserId;
import io.riwi.messaging.domain.port.MessageRepository;

/** Consulta 2: búsqueda con resaltado, paginada por keyset. */
public class SearchMessagesUseCase {
    private static final int MAX_PAGE_SIZE = 100;

    private final MessageRepository messageRepository;

    public SearchMessagesUseCase(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public KeysetPage<MessageSearchResult> execute(UserId actorId, String term, Long cursor, int limit) {
        if (term == null || term.isBlank()) {
            throw new ValidationException("el término de búsqueda no puede estar vacío");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new ValidationException("limit debe estar entre 1 y " + MAX_PAGE_SIZE);
        }
        return messageRepository.search(actorId, term, cursor, limit);
    }
}
