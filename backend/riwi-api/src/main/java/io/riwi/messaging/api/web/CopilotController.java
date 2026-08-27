package io.riwi.messaging.api.web;

import io.riwi.messaging.api.security.CurrentActor;
import io.riwi.messaging.api.web.dto.AskCopilotRequest;
import io.riwi.messaging.api.web.dto.CopilotAnswerResponse;
import io.riwi.messaging.api.web.dto.CopilotUsageResponse;
import io.riwi.messaging.application.copilot.AskCopilotUseCase;
import io.riwi.messaging.application.copilot.GetCopilotUsageUseCase;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/copilot")
public class CopilotController {
    private final AskCopilotUseCase askCopilotUseCase;
    private final GetCopilotUsageUseCase getCopilotUsageUseCase;

    public CopilotController(AskCopilotUseCase askCopilotUseCase, GetCopilotUsageUseCase getCopilotUsageUseCase) {
        this.askCopilotUseCase = askCopilotUseCase;
        this.getCopilotUsageUseCase = getCopilotUsageUseCase;
    }

    @PostMapping("/ask")
    public CopilotAnswerResponse ask(@AuthenticationPrincipal CurrentActor actor, @Valid @RequestBody AskCopilotRequest request) {
        return CopilotAnswerResponse.from(askCopilotUseCase.execute(actor.userId(), request.question()));
    }

    // Consulta 4.
    @GetMapping("/usage")
    public CopilotUsageResponse usage(@AuthenticationPrincipal CurrentActor actor) {
        return CopilotUsageResponse.from(getCopilotUsageUseCase.execute(actor.userId()));
    }
}
