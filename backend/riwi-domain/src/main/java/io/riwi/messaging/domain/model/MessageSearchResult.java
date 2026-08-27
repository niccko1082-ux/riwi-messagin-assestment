package io.riwi.messaging.domain.model;

/** Resultado de la Consulta 2 (búsqueda con resaltado). highlightedContent trae los
 *  términos encontrados envueltos, p. ej. con &lt;b&gt;...&lt;/b&gt; (ts_headline). */
public record MessageSearchResult(
        MessageId id,
        ChannelId channelId,
        String highlightedContent,
        double rank
) {
}
