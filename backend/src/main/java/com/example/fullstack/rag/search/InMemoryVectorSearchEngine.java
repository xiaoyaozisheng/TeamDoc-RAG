package com.example.fullstack.rag.search;

import com.example.fullstack.entity.Chunk;
import com.example.fullstack.repository.ChunkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 内存余弦检索（默认实现）：H2 / 小规模 PG 均可运行，MVP 规模（数万块内）性能足够。
 * 租户过滤在 SQL 层强制执行（findSearchableByTenant）。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector-engine", havingValue = "memory", matchIfMissing = true)
public class InMemoryVectorSearchEngine implements VectorSearchEngine {

    private final ChunkRepository chunkRepository;
    private final ObjectMapper objectMapper;

    @Override
    public List<ScoredChunk> search(Long tenantId, float[] queryEmbedding, int topK) {
        List<Chunk> candidates = chunkRepository.findSearchableByTenant(tenantId);
        record Hit(Chunk chunk, double score) {}

        List<Hit> hits = new ArrayList<>(candidates.size());
        for (Chunk chunk : candidates) {
            float[] vector = deserialize(chunk.getEmbedding());
            if (vector == null) {
                continue;
            }
            double score = cosine(queryEmbedding, vector);
            hits.add(new Hit(chunk, score));
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());

        return hits.stream()
                .limit(topK)
                .map(hit -> new ScoredChunk(
                        hit.chunk().getId(),
                        hit.chunk().getDocument().getId(),
                        hit.chunk().getDocument().getName(),
                        hit.chunk().getPageNumber(),
                        hit.chunk().getContent(),
                        hit.score(),
                        null,
                        hit.score()))
                .toList();
    }

    private float[] deserialize(String json) {
        try {
            List<Number> list = objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, Number.class));
            float[] vector = new float[list.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = list.get(i).floatValue();
            }
            return vector;
        } catch (Exception e) {
            return null;
        }
    }

    static double cosine(float[] a, float[] b) {
        int length = Math.min(a.length, b.length);
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
