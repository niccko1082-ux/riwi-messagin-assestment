package io.riwi.messaging.api.web;

import io.riwi.messaging.api.security.CurrentActor;
import io.riwi.messaging.api.web.dto.ConversationSummaryResponse;
import io.riwi.messaging.application.messaging.ListConversationsUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ConversationController {
    private final ListConversationsUseCase listConversationsUseCase;

    public ConversationController(ListConversationsUseCase listConversationsUseCase) {
        this.listConversationsUseCase = listConversationsUseCase;
    }

    @GetMapping("/conversations")
    public List<ConversationSummaryResponse> list(@AuthenticationPrincipal CurrentActor actor) {
        return listConversationsUseCase.execute(actor.userId()).stream()
                .map(ConversationSummaryResponse::from)
                .toList();
    }
}
