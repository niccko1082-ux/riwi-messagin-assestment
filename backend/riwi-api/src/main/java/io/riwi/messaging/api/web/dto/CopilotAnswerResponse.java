package io.riwi.messaging.api.web.dto;

import io.riwi.messaging.domain.model.CopilotAnswer;

import java.util.List;

public record CopilotAnswerResponse(String answer, boolean hadSufficientContext, String systemPromptVersion,
                                     Integer tokensUsed, List<CitationResponse> citations) {
    public static CopilotAnswerResponse from(CopilotAnswer a) {
        return new CopilotAnswerResponse(a.answer(), a.hadSufficientContext(), a.systemPromptVersion(),
                a.tokensUsed(), a.citations().stream().map(CitationResponse::from).toList());
    }
}
