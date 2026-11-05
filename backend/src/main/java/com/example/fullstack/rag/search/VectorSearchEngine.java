package com.example.fullstack.rag.search;

import java.util.List;

/** 向量检索引擎：租户过滤必须在此层强制执行（RET-03）。 */
public interface VectorSearchEngine {

    /**
     * @param queryEmbedding 查询向量
     * @param topK           返回条数
     */
    List<ScoredChunk> search(Long tenantId, float[] queryEmbedding, int topK);
}
