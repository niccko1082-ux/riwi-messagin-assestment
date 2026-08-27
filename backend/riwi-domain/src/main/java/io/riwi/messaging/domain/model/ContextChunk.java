package io.riwi.messaging.domain.model;

/** Fila de rw_copilot_search_context (Consulta 3): ya filtrada por canales del actor. */
public record ContextChunk(MessageId messageId, ChannelId channelId, String content, double similarity) {
}
