package io.riwi.messaging.domain.model;

public record PendingEmbeddingJob(long jobId, long messageId, String content) {
}
