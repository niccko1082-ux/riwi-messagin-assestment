package io.riwi.messaging.domain.model;

import java.time.Instant;

/** Consulta 4: uso acumulado del copiloto por usuario. */
public record CopilotUsageSummary(long totalQueries, long totalTokensUsed, Instant lastQueryAt) {
}
