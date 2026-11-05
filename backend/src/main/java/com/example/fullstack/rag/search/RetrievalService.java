package com.example.fullstack.rag.search;

import com.example.fullstack.config.RagProperties;
import com.example.fullstack.entity.Chunk;
import com.example.fullstack.entity.RetrievalLog;
import com.example.fullstack.entity.Tenant;
import com.example.fullstack.rag.embedding.EmbeddingService;
import com.example.fullstack.repository.ChunkRepository;
import com.example.fullstack.repository.RetrievalLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 混合检索（RET-01 ~ RET-05）：向量 + 全文双路召回，RRF 融合，
 * 检索日志落库（可排查"为什么答错"）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final VectorSearchEngine vectorSearchEngine;
    private final KeywordSearchEngine keywordSearchEngine;
    private final EmbeddingService embeddingService;
    private final ChunkRepository chunkRepository;
    private final RetrievalLogRepository retrievalLogRepository;
    private final RagProperties properties;
    private final ObjectMapper objectMapper;

    public List<ScoredChunk> search(Tenant tenant, String query) {
        long start = System.currentTimeMillis();
        RagProperties.Retrieval cfg = properties.getRetrieval();

        float[] queryEmbedding = embeddingService.embed(query);
        List<ScoredChunk> vectorHits = vectorSearchEngine.search(
                tenant.getId(), queryEmbedding, cfg.getVectorCandidates());
        List<ScoredChunk> keywordHits = keywordSearchEngine.search(
                tenant.getId(), query, cfg.getKeywordCandidates());

        List<ScoredChunk> fused = RrfFuser.fuse(vectorHits, keywordHits, cfg.getRrfK(), cfg.getTopK());

        long latency = System.currentTimeMillis() - start;
        logRetrieval(tenant, query, fused, vectorHits.size() + keywordHits.size(), latency);
        return fused;
    }

    /** 加载父块内容供 LLM 使用（ING-03：子块检索，父块供生成）。返回 子块ID -> 父块内容 */
    public Map<Long, String> loadParentContents(List<ScoredChunk> hits) {
        if (hits.isEmpty()) {
            return Map.of();
        }
        List<Chunk> children = chunkRepository.findByIdIn(
                hits.stream().map(ScoredChunk::chunkId).toList());
        List<Long> parentIds = children.stream()
                .map(Chunk::getParentChunkId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (parentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> parentById = new HashMap<>();
        chunkRepository.findByIdIn(parentIds)
                .forEach(parent -> parentById.put(parent.getId(), parent.getContent()));
        Map<Long, String> result = new HashMap<>();
        for (Chunk child : children) {
            String parent = child.getParentChunkId() == null ? null : parentById.get(child.getParentChunkId());
            if (parent != null) {
                result.put(child.getId(), parent);
            }
        }
        return result;
    }

    private void logRetrieval(Tenant tenant, String query, List<ScoredChunk> fused,
                              int candidateCount, long latencyMs) {
        try {
            List<Map<String, Object>> results = fused.stream()
                    .map(hit -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("chunkId", hit.chunkId());
                        item.put("documentId", hit.documentId());
                        item.put("documentName", hit.documentName());
                        item.put("pageNumber", hit.pageNumber());
                        item.put("rrfScore", hit.rrfScore());
                        return item;
                    })
                    .toList();
            retrievalLogRepository.save(RetrievalLog.builder()
                    .tenant(tenant)
                    .query(query)
                    .topK(properties.getRetrieval().getTopK())
                    .candidateCount(candidateCount)
                    .resultJson(objectMapper.writeValueAsString(results))
                    .latencyMs(latencyMs)
                    .build());
        } catch (Exception e) {
            log.warn("检索日志写入失败", e);
        }
    }
}
