package io.riwi.messaging.application.copilot;

import io.riwi.messaging.domain.model.CopilotUsageSummary;
import io.riwi.messaging.domain.model.UserId;
import io.riwi.messaging.domain.port.CopilotRepository;

public class GetCopilotUsageUseCase {
    private final CopilotRepository copilotRepository;

    public GetCopilotUsageUseCase(CopilotRepository copilotRepository) {
        this.copilotRepository = copilotRepository;
    }

    public CopilotUsageSummary execute(UserId actorId) {
        return copilotRepository.getUsageSummary(actorId);
    }
}
