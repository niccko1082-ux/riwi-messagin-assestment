package io.riwi.messaging.api.web;

import io.riwi.messaging.api.security.CurrentActor;
import io.riwi.messaging.api.web.dto.KeysetPageResponse;
import io.riwi.messaging.api.web.dto.MessageSearchResultResponse;
import io.riwi.messaging.application.messaging.SearchMessagesUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta 2: búsqueda con resaltado, paginada por keyset. */
@RestController
@RequestMapping("/api")
public class SearchController {
    private final SearchMessagesUseCase searchMessagesUseCase;

    public SearchController(SearchMessagesUseCase searchMessagesUseCase) {
        this.searchMessagesUseCase = searchMessagesUseCase;
    }

    @GetMapping("/messages/search")
    public KeysetPageResponse<MessageSearchResultResponse> search(@AuthenticationPrincipal CurrentActor actor,
                                                                    @RequestParam String term,
                                                                    @RequestParam(required = false) Long cursor,
                                                                    @RequestParam(defaultValue = "30") int limit) {
        var page = searchMessagesUseCase.execute(actor.userId(), term, cursor, limit);
        return KeysetPageResponse.from(page, MessageSearchResultResponse::from);
    }
}
