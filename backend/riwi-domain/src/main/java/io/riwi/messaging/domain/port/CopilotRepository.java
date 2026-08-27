package io.riwi.messaging.domain.port;

import io.riwi.messaging.domain.model.*;

import java.util.List;

public interface CopilotRepository {
    /** Consulta 3: contexto RAG ya acotado a los canales del actor (rw_my_channel_ids()). */
    List<ContextChunk> searchContext(UserId actorId, float[] queryEmbedding, int matchCount);

    long logQuery(UserId actorId, String question, String answer, Integer tokensUsed,
                   boolean hadSufficientContext, String systemPromptVersion, List<Citation> citations);

    /** Consulta 4: uso acumulado del copiloto. RLS ya acota rw_copilot_queries al propio
     *  usuario (sel_rw_copilot_queries), el filtro por actorId es defensa en profundidad. */
    CopilotUsageSummary getUsageSummary(UserId actorId);
}
