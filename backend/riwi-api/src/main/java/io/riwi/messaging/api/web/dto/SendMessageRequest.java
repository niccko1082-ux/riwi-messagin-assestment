package io.riwi.messaging.api.web.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(@NotBlank String content) {
}
