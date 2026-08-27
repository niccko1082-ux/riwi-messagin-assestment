package io.riwi.messaging.api.web.dto;

/** Tipo de evento publicado por WebSocket a /topic/channels/{channelId}. */
public enum MessageEventType {
    SENT, EDITED, DELETED
}
