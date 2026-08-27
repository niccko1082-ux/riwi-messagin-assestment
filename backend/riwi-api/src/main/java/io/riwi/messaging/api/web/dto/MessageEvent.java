package io.riwi.messaging.api.web.dto;

public record MessageEvent(MessageEventType type, long messageId, MessageResponse message) {
}
