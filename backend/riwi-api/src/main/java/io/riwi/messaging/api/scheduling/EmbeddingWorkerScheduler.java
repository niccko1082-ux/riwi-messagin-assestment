package io.riwi.messaging.api.scheduling;

import io.riwi.messaging.application.copilot.ProcessEmbeddingJobsUseCase;
import io.riwi.messaging.infrastructure.ai.NvidiaNimProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingWorkerScheduler {
    private final ProcessEmbeddingJobsUseCase processEmbeddingJobsUseCase;
    private final NvidiaNimProperties properties;

    public EmbeddingWorkerScheduler(ProcessEmbeddingJobsUseCase processEmbeddingJobsUseCase, NvidiaNimProperties properties) {
        this.processEmbeddingJobsUseCase = processEmbeddingJobsUseCase;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 10_000)
    public void run() {
        processEmbeddingJobsUseCase.execute(properties.embeddingModel());
    }
}
