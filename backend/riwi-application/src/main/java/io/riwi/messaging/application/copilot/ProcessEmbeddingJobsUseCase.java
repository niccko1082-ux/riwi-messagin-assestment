package io.riwi.messaging.application.copilot;

import io.riwi.messaging.domain.model.PendingEmbeddingJob;
import io.riwi.messaging.domain.port.EmbeddingJobRepository;
import io.riwi.messaging.domain.port.EmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Worker del outbox de embeddings (trigger de Fase 3 -> aquí). Un job fallido queda en
 *  'failed', no bloquea a los demás. */
public class ProcessEmbeddingJobsUseCase {
    private static final Logger log = LoggerFactory.getLogger(ProcessEmbeddingJobsUseCase.class);
    private static final int BATCH_SIZE = 20;

    private final EmbeddingJobRepository jobRepository;
    private final EmbeddingProvider embeddingProvider;

    public ProcessEmbeddingJobsUseCase(EmbeddingJobRepository jobRepository, EmbeddingProvider embeddingProvider) {
        this.jobRepository = jobRepository;
        this.embeddingProvider = embeddingProvider;
    }

    public int execute(String embeddingModel) {
        List<PendingEmbeddingJob> jobs = jobRepository.fetchPending(BATCH_SIZE);
        for (PendingEmbeddingJob job : jobs) {
            try {
                float[] embedding = embeddingProvider.embedPassage(job.content());
                jobRepository.recordEmbedding(job.jobId(), embedding, embeddingModel);
            } catch (RuntimeException e) {
                log.warn("job de embedding {} falló (message_id={}): {}", job.jobId(), job.messageId(), e.getMessage());
                jobRepository.failJob(job.jobId());
            }
        }
        return jobs.size();
    }
}
