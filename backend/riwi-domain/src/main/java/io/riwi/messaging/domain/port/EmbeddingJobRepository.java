package io.riwi.messaging.domain.port;

import io.riwi.messaging.domain.model.PendingEmbeddingJob;

import java.util.List;

/** Outbox de embeddings (Fase 3 trigger -> Fase 5 worker). Corre con el rol rw_worker. */
public interface EmbeddingJobRepository {
    List<PendingEmbeddingJob> fetchPending(int limit);

    void recordEmbedding(long jobId, float[] embedding, String embeddingModel);

    void failJob(long jobId);
}
