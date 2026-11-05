package com.example.fullstack.rag.search;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF 融合（RET-02）：score = Σ 1/(k + rank_i)。
 * 不依赖两路分数量纲一致，适合起步阶段；纯函数便于单测。
 */
public final class RrfFuser {

    private RrfFuser() {}

    /**
     * @param k 常数（默认 60），越大排名靠后项的差距越平缓
     */
    public static List<ScoredChunk> fuse(List<ScoredChunk> vectorResults,
                                         List<ScoredChunk> keywordResults,
                                         int k, int topK) {
        Map<Long, ScoredChunk> byId = new LinkedHashMap<>();
        Map<Long, Double> scores = new LinkedHashMap<>();

        accumulate(vectorResults, k, byId, scores);
        accumulate(keywordResults, k, byId, scores);

        return byId.values().stream()
                .sorted((a, b) -> Double.compare(scores.get(b.chunkId()), scores.get(a.chunkId())))
                .limit(topK)
                .map(chunk -> new ScoredChunk(
                        chunk.chunkId(), chunk.documentId(), chunk.documentName(),
                        chunk.pageNumber(), chunk.content(),
                        chunk.vectorScore(), chunk.keywordScore(),
                        scores.get(chunk.chunkId())))
                .toList();
    }

    private static void accumulate(List<ScoredChunk> results, int k,
                                   Map<Long, ScoredChunk> byId, Map<Long, Double> scores) {
        for (int rank = 0; rank < results.size(); rank++) {
            ScoredChunk chunk = results.get(rank);
            byId.putIfAbsent(chunk.chunkId(), chunk);
            scores.merge(chunk.chunkId(), 1.0 / (k + rank + 1), Double::sum);
        }
    }
}
