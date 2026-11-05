package com.example.fullstack.rag.embedding;

import com.example.fullstack.config.RagProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final HashingEmbeddingProvider hashingProvider;
    private final OpenAiEmbeddingProvider openAiProvider;
    private final RagProperties properties;

    private EmbeddingProvider provider() {
        return switch (properties.getEmbedding().getProvider()) {
            case "openai" -> openAiProvider;
            default -> hashingProvider;
        };
    }

    public int dimensions() {
        return provider().dimensions();
    }

    public float[] embed(String text) {
        return provider().embed(text);
    }

    public List<float[]> embedBatch(List<String> texts) {
        return provider().embedBatch(texts);
    }
}
