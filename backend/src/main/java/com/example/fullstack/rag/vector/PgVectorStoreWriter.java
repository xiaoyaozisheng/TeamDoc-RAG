package com.example.fullstack.rag.vector;

import com.example.fullstack.rag.ingestion.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * pgvector 写入器（TEN-02 / ING-04）：仅当 rag.pgvector.enabled=true（postgres Profile）时生效。
 * metadata 携带 tenant_id/document_id/page_number，支撑检索层租户过滤与引用溯源。
 * 表结构见 db/postgres/001_init_vector_store.sql。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.pgvector.enabled", havingValue = "true")
public class PgVectorStoreWriter {

    private final JdbcTemplate jdbcTemplate;

    public void replaceDocument(Long tenantId, Long documentId,
                                List<IngestionService.ChildBlockView> children,
                                List<float[]> vectors, int dimensions) {
        jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'document_id' = ?",
                String.valueOf(documentId));

        String insert = "INSERT INTO vector_store (content, metadata, embedding) VALUES (?, ?::json, ?::vector)";
        for (int i = 0; i < children.size(); i++) {
            IngestionService.ChildBlockView child = children.get(i);
            String metadata = "{\"tenant_id\": \"%s\", \"document_id\": \"%s\", \"chunk_id\": \"%s\", \"page_number\": %d}"
                    .formatted(tenantId, documentId, child.chunkId(), child.pageNumber());
            jdbcTemplate.update(insert,
                    child.content(),
                    metadata,
                    toVectorLiteral(vectors.get(i)));
        }
        log.info("pgvector 写入完成: document={}, chunks={}, dims={}", documentId, children.size(), dimensions);
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
