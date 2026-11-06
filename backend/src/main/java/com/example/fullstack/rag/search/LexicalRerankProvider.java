package com.example.fullstack.rag.search;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 本地词面重排器（RET-07 默认实现，零外部依赖）：
 * 对查询与片段内容做字符 bigram 覆盖率（对中英文都稳健），
 * 与 RRF 分数归一化后加权融合。可替换为交叉编码器（cross-encoder）模型实现同一接口。
 */
@Component
public class LexicalRerankProvider implements RerankProvider {

    /** 词面得分权重；RRF 排名信息仍保留 0.3 权重，避免纯词面误伤语义命中 */
    static final double LEXICAL_WEIGHT = 0.7;
    static final double RRF_WEIGHT = 0.3;

    @Override
    public List<ScoredChunk> rerank(String query, List<ScoredChunk> candidates, int topK) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }
        Set<String> queryBigrams = bigrams(query);

        double maxRrf = candidates.stream()
                .mapToDouble(ScoredChunk::rrfScore)
                .max()
                .orElse(1.0);

        List<ScoredChunk> reranked = new ArrayList<>();
        for (ScoredChunk candidate : candidates) {
            double lexical = lexicalScore(queryBigrams, candidate.content());
            double rrfNorm = maxRrf > 0 ? candidate.rrfScore() / maxRrf : 0.0;
            double finalScore = LEXICAL_WEIGHT * lexical + RRF_WEIGHT * rrfNorm;

            reranked.add(new ScoredChunk(
                    candidate.chunkId(),
                    candidate.documentId(),
                    candidate.documentName(),
                    candidate.pageNumber(),
                    candidate.content(),
                    candidate.vectorScore(),
                    candidate.keywordScore(),
                    round(finalScore)));
        }

        reranked.sort((a, b) -> Double.compare(b.rrfScore(), a.rrfScore()));
        return reranked.subList(0, Math.min(topK, reranked.size())).stream().toList();
    }

    /** 词面覆盖率：查询 bigram 在片段中出现的比例 */
    private double lexicalScore(Set<String> queryBigrams, String content) {
        if (queryBigrams.isEmpty()) {
            return 0.0;
        }
        Set<String> contentBigrams = bigrams(content);
        long hits = queryBigrams.stream().filter(contentBigrams::contains).count();
        return (double) hits / queryBigrams.size();
    }

    /** 字符 bigram 集合（去空白），中文单字粒度足够区分关键词命中 */
    static Set<String> bigrams(String text) {
        Set<String> result = new HashSet<>();
        if (text == null) {
            return result;
        }
        String normalized = text.replaceAll("\\s+", "");
        for (int i = 0; i + 2 <= normalized.length(); i++) {
            result.add(normalized.substring(i, i + 2));
        }
        if (normalized.isEmpty()) {
            return result;
        }
        if (result.isEmpty() && !normalized.isEmpty()) {
            // 单字符查询/内容：退化为 unigram
            result.add(normalized);
        }
        return result;
    }

    private static double round(double value) {
        return Math.round(value * 1e6) / 1e6;
    }
}
