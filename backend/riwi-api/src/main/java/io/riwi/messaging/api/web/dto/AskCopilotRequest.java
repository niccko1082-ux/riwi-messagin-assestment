package io.riwi.messaging.api.web.dto;

import jakarta.validation.constraints.NotBlank;

public record AskCopilotRequest(@NotBlank String question) {
}
