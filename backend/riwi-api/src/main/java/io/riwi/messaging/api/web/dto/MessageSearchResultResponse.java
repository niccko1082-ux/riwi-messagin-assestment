package io.riwi.messaging.api.web.dto;

import io.riwi.messaging.domain.model.MessageSearchResult;

import java.util.UUID;

public record MessageSearchResultResponse(long id, UUID channelId, String highlightedContent, double rank) {
    public static MessageSearchResultResponse from(MessageSearchResult r) {
        return new MessageSearchResultResponse(r.id().value(), r.channelId().value(), r.highlightedContent(), r.rank());
    }
}
