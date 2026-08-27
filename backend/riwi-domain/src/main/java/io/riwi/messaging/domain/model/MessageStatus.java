package io.riwi.messaging.domain.model;

/** Refleja la columna generada rw_messages.status (Fase 3). */
public enum MessageStatus {
    SENT, EDITED, DELETED;

    public static MessageStatus fromDb(String raw) {
        return MessageStatus.valueOf(raw.toUpperCase());
    }
}
