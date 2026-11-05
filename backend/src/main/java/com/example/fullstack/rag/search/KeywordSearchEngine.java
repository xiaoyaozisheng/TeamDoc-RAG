package com.example.fullstack.rag.search;

import com.example.fullstack.repository.ChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 关键词检索引擎（RET-01 第二路召回）：对型号、编号、条款号等精确标识符，
 * 向量召回差，全文检索补位。
 */
public interface KeywordSearchEngine {

    List<ScoredChunk> search(Long tenantId, String query, int topK);

    /** H2 兜底实现：分词后 LIKE 计数打分（无全文索引能力） */
    @Component
    @RequiredArgsConstructor
    @ConditionalOnProperty(name = "rag.keyword-engine", havingValue = "h2", matchIfMissing = true)
    class H2KeywordSearchEngine implements KeywordSearchEngine {

        private final ChunkRepository chunkRepository;

        @Override
        public List<ScoredChunk> search(Long tenantId, String query, int topK) {
            List<String> terms = tokenize(query);
            if (terms.isEmpty()) {
                return List.of();
            }
            Map<Long, ScoredChunk> best = new HashMap<>();
            for (String term : terms) {
                if (term.length() < 2) {
                    continue;
                }
                for (com.example.fullstack.entity.Chunk chunk : chunkRepository.searchByTerm(tenantId, term)) {
                    int occurrences = countOccurrences(chunk.getContent().toLowerCase(), term.toLowerCase());
                    if (occurrences == 0) {
                        continue;
                    }
                    double score = occurrences * (double) term.length() / Math.max(1, chunk.getContent().length());
                    best.merge(chunk.getId(), new ScoredChunk(
                                    chunk.getId(), chunk.getDocument().getId(), chunk.getDocument().getName(),
                                    chunk.getPageNumber(), chunk.getContent(), null, score, score),
                            (oldScore, newScore) -> newScore.keywordScore() > oldScore.keywordScore() ? newScore : oldScore);
                }
            }
            return best.values().stream()
                    .sorted(java.util.Comparator.comparingDouble(ScoredChunk::rrfScore).reversed())
                    .limit(topK)
                    .toList();
        }

        private List<String> tokenize(String query) {
            // 中文按 2-gram + 空格分词，兼顾短语与单字词
            List<String> terms = new ArrayList<>(List.of(query.trim().split("\\s+")));
            String compact = query.replaceAll("\\s+", "");
            for (int i = 0; i + 1 < compact.length(); i++) {
                terms.add(compact.substring(i, i + 2));
            }
            return terms;
        }

        private int countOccurrences(String text, String term) {
            int count = 0;
            int index = 0;
            while ((index = text.indexOf(term, index)) >= 0) {
                count++;
                index += term.length();
            }
            return count;
        }
    }

    /** PostgreSQL 实现：tsvector 全文检索 + ts_rank 打分 */
    @Component
    @RequiredArgsConstructor
    @ConditionalOnProperty(name = "rag.keyword-engine", havingValue = "pg")
    class PgKeywordSearchEngine implements KeywordSearchEngine {

        private final JdbcTemplate jdbcTemplate;

        @Override
        public List<ScoredChunk> search(Long tenantId, String query, int topK) {
            String sql = """
                    SELECT c.id, c.content, c.page_number, d.id AS doc_id, d.name AS doc_name,
                           ts_rank(to_tsvector('simple', c.content), plainto_tsquery('simple', ?)) AS rank
                    FROM chunks c
                    JOIN documents d ON d.id = c.document_id
                    WHERE c.tenant_id = ? AND d.deleted = false
                      AND to_tsvector('simple', c.content) @@ plainto_tsquery('simple', ?)
                    ORDER BY rank DESC
                    LIMIT ?
                    """;
            return jdbcTemplate.query(sql, (rs, rowNum) -> new ScoredChunk(
                    rs.getLong("id"),
                    rs.getLong("doc_id"),
                    rs.getString("doc_name"),
                    rs.getInt("page_number"),
                    rs.getString("content"),
                    null,
                    rs.getDouble("rank"),
                    rs.getDouble("rank")), query, tenantId, query, topK);
        }
    }
}
