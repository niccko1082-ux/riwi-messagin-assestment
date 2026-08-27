package io.riwi.messaging.domain.model;

import java.util.List;

public record CopilotAnswer(
        String answer,
        boolean hadSufficientContext,
        String systemPromptVersion,
        Integer tokensUsed,
        List<Citation> citations
) {
}
