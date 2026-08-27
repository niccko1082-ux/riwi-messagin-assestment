package io.riwi.messaging.infrastructure.ai;

import io.riwi.messaging.domain.exception.AiProviderException;
import io.riwi.messaging.domain.model.ChatCompletionResult;
import io.riwi.messaging.domain.model.ChatMessage;
import io.riwi.messaging.domain.port.ChatCompletionProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class NvidiaChatCompletionAdapter implements ChatCompletionProvider {
    private static final int MAX_TOKENS = 1024;

    private final RestClient restClient;
    private final NvidiaNimProperties properties;

    public NvidiaChatCompletionAdapter(RestClient nvidiaRestClient, NvidiaNimProperties properties) {
        this.restClient = nvidiaRestClient;
        this.properties = properties;
    }

    @Override
    public ChatCompletionResult complete(List<ChatMessage> messages) {
        List<NvidiaDtos.ChatMessageDto> dtoMessages = messages.stream()
                .map(m -> new NvidiaDtos.ChatMessageDto(m.role().name().toLowerCase(), m.content()))
                .toList();
        try {
            NvidiaDtos.ChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(new NvidiaDtos.ChatRequest(properties.chatModel(), dtoMessages, MAX_TOKENS))
                    .retrieve()
                    .body(NvidiaDtos.ChatResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new AiProviderException("NVIDIA NIM no devolvió ninguna respuesta de chat");
            }
            String content = response.choices().get(0).message().content();
            // Modelos de razonamiento (gpt-oss) pueden agotar max_tokens en "reasoning" y
            // devolver content=null sin error HTTP — tratarlo como null silencioso ocultaría
            // la falla en vez de reintentarla/reportarla.
            if (content == null || content.isBlank()) {
                throw new AiProviderException("NVIDIA NIM no devolvió contenido (posible límite de tokens agotado en razonamiento)");
            }
            Integer tokensUsed = response.usage() != null ? response.usage().total_tokens() : null;
            return new ChatCompletionResult(content, tokensUsed);
        } catch (RestClientException e) {
            throw new AiProviderException("fallo al llamar al chat completion de NVIDIA NIM: " + e.getMessage());
        }
    }
}
