package io.riwi.messaging.domain.port;

import io.riwi.messaging.domain.model.ChatCompletionResult;
import io.riwi.messaging.domain.model.ChatMessage;

import java.util.List;

/** Interfaz intercambiable de chat completion (compatible con SDK estilo OpenAI). */
public interface ChatCompletionProvider {
    ChatCompletionResult complete(List<ChatMessage> messages);
}
