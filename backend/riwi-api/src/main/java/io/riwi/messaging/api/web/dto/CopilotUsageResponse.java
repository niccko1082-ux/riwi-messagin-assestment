package io.riwi.messaging.api.web.dto;

import io.riwi.messaging.domain.model.CopilotUsageSummary;

import java.time.Instant;

public record CopilotUsageResponse(long totalQueries, long totalTokensUsed, Instant lastQueryAt) {
    public static CopilotUsageResponse from(CopilotUsageSummary s) {
        return new CopilotUsageResponse(s.totalQueries(), s.totalTokensUsed(), s.lastQueryAt());
    }
}
