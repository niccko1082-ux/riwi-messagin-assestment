package io.riwi.messaging.infrastructure.ai;

import io.riwi.messaging.domain.exception.AiProviderException;
import io.riwi.messaging.domain.port.EmbeddingProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class NvidiaEmbeddingAdapter implements EmbeddingProvider {
    private final RestClient restClient;
    private final NvidiaNimProperties properties;

    public NvidiaEmbeddingAdapter(RestClient nvidiaRestClient, NvidiaNimProperties properties) {
        this.restClient = nvidiaRestClient;
        this.properties = properties;
    }

    @Override
    public float[] embedPassage(String text) {
        return embed(text, "passage");
    }

    @Override
    public float[] embedQuery(String text) {
        return embed(text, "query");
    }

    private float[] embed(String text, String inputType) {
        try {
            NvidiaDtos.EmbeddingResponse response = restClient.post()
                    .uri("/embeddings")
                    .body(new NvidiaDtos.EmbeddingRequest(properties.embeddingModel(), List.of(text), inputType, 1024))
                    .retrieve()
                    .body(NvidiaDtos.EmbeddingResponse.class);
            if (response == null || response.data() == null || response.data().isEmpty()) {
                throw new AiProviderException("NVIDIA NIM no devolvió ningún embedding");
            }
            List<Float> values = response.data().get(0).embedding();
            float[] embedding = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                embedding[i] = values.get(i);
            }
            return embedding;
        } catch (RestClientException e) {
            throw new AiProviderException("fallo al llamar al servicio de embeddings de NVIDIA NIM: " + e.getMessage());
        }
    }
}
