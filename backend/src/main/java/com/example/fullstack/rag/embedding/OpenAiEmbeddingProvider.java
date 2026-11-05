package com.example.fullstack.rag.embedding;

import com.example.fullstack.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Embedding 实现（OpenAI / Azure / 各类自建兼容网关均可）。
 * 仅在 rag.embedding.provider=openai 且配置了 API Key 时由 EmbeddingService 启用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    private final RagProperties properties;

    private RestClient client;

    private RestClient client() {
        if (client == null) {
            RagProperties.Embedding.OpenAi cfg = properties.getEmbedding().getOpenai();
            this.client = RestClient.builder()
                    .baseUrl(cfg.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + cfg.getApiKey())
                    .build();
        }
        return client;
    }

    @Override
    public int dimensions() {
        return properties.getEmbedding().getDimensions();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<float[]> embedBatch(List<String> texts) {
        RagProperties.Embedding.OpenAi cfg = properties.getEmbedding().getOpenai();
        Map<String, Object> response = client().post()
                .uri("/v1/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", cfg.getModel(), "input", texts))
                .retrieve()
                .body(Map.class);
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        return data.stream()
                .sorted(java.util.Comparator.comparingInt(item -> (Integer) item.get("index")))
                .map(item -> {
                    List<Number> embedding = (List<Number>) item.get("embedding");
                    float[] vector = new float[embedding.size()];
                    for (int i = 0; i < vector.length; i++) {
                        vector[i] = embedding.get(i).floatValue();
                    }
                    return vector;
                })
                .toList();
    }
}
