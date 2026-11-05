package com.example.fullstack.rag.vector;

import com.example.fullstack.rag.search.ScoredChunk;
import com.example.fullstack.rag.search.VectorSearchEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * pgvector 检索（HNSW + 余弦距离 <=>）：postgres Profile 启用。
 * 租户过滤在 SQL 的 metadata 上强制执行（RET-03），deleted 文档不进入候选集。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector-engine", havingValue = "pgvector")
public class PgVectorSearchEngine implements VectorSearchEngine {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<ScoredChunk> search(Long tenantId, float[] queryEmbedding, int topK) {
        String sql = """
                SELECT c.id, c.content, c.page_number, d.id AS doc_id, d.name AS doc_name,
                       1 - (v.embedding <=> ?::vector) AS score
                FROM vector_store v
                JOIN chunks c ON c.id = (v.metadata->>'chunk_id')::bigint
                JOIN documents d ON d.id = c.document_id
                WHERE v.metadata->>'tenant_id' = ? AND d.deleted = false
                ORDER BY v.embedding <=> ?::vector
                LIMIT ?
                """;
        String vectorLiteral = toVectorLiteral(queryEmbedding);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ScoredChunk(
                rs.getLong("id"),
                rs.getLong("doc_id"),
                rs.getString("doc_name"),
                rs.getInt("page_number"),
                rs.getString("content"),
                rs.getDouble("score"),
                null,
                rs.getDouble("score")), vectorLiteral, String.valueOf(tenantId), vectorLiteral, topK);
    }

    private String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
