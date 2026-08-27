package io.riwi.messaging.api.web.dto;

import io.riwi.messaging.domain.model.Citation;

public record CitationResponse(long messageId, double similarityScore) {
    public static CitationResponse from(Citation c) {
        return new CitationResponse(c.messageId().value(), c.similarityScore());
    }
}
