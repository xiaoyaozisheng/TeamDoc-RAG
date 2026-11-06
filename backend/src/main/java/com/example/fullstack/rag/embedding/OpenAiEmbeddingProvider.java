package com.example.fullstack.rag.embedding;

import com.example.fullstack.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Embedding 实现（OpenAI / Azure / 各类自建兼容网关均可）。
 * 仅在 rag.embedding.provider=openai 且配置了 API Key 时由 EmbeddingService 启用。
 * 客户端按当前 baseUrl/apiKey 惰性构建：运行时改配置后自动重建。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    /** 单次请求最多条数：整文档一次请求会触发 413/超限 */
    private static final int MAX_BATCH = 16;
    private static final int TIMEOUT_SECONDS = 60;

    private final RagProperties properties;

    private RestClient client;
    private String clientKey = "";

    private RestClient client() {
        RagProperties.Embedding.OpenAi cfg = properties.getEmbedding().getOpenai();
        String key = cfg.getBaseUrl() + "|" + cfg.getApiKey();
        if (client == null || !key.equals(clientKey)) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(10));
            factory.setReadTimeout(Duration.ofSeconds(TIMEOUT_SECONDS));
            this.client = RestClient.builder()
                    .baseUrl(cfg.getBaseUrl())
                    .requestFactory(factory)
                    .defaultHeader("Authorization", "Bearer " + cfg.getApiKey())
                    .build();
            this.clientKey = key;
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
        List<float[]> result = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += MAX_BATCH) {
            List<String> batch = texts.subList(i, Math.min(i + MAX_BATCH, texts.size()));
            result.addAll(post(batch));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<float[]> post(List<String> texts) {
        RagProperties.Embedding.OpenAi cfg = properties.getEmbedding().getOpenai();
        Map<String, Object> response = client().post()
                .uri("/v1/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", cfg.getModel(), "input", texts))
                .retrieve()
                .body(Map.class);
        if (response == null || response.get("data") == null) {
            throw new IllegalStateException("Embedding 响应缺少 data 字段");
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        return data.stream()
                .sorted(java.util.Comparator.comparingInt(item -> (Integer) item.get("index")))
                .map(item -> {
                    List<Number> embedding = (List<Number>) item.get("embedding");
                    if (embedding.size() != properties.getEmbedding().getDimensions()) {
                        throw new IllegalStateException(
                                "Embedding 返回维度 %d 与配置 rag.embedding.dimensions=%d 不一致，请核对模型/配置"
                                        .formatted(embedding.size(), properties.getEmbedding().getDimensions()));
                    }
                    float[] vector = new float[embedding.size()];
                    for (int i = 0; i < vector.length; i++) {
                        vector[i] = embedding.get(i).floatValue();
                    }
                    return vector;
                })
                .toList();
    }
}
