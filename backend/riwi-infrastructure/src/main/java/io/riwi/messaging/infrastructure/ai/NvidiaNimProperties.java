package io.riwi.messaging.infrastructure.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "riwi.ai.nvidia")
public record NvidiaNimProperties(String baseUrl, String apiKey, String chatModel, String embeddingModel) {
}
