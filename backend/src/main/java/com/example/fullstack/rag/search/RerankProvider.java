package com.example.fullstack.rag.search;

import java.util.List;

/**
 * RET-07：Reranker 适配层。对 RRF 融合后的候选（默认 Top20）重排，取 TopK。
 * 默认实现为本地词面重排（LexicalRerankProvider），可替换为交叉编码器模型。
 */
public interface RerankProvider {

    /**
     * @param query      原始查询
     * @param candidates RRF 融合后的候选（已按 rrfScore 降序）
     * @param topK       重排后保留的条数
     * @return 重排后的候选（按最终得分降序，最多 topK 条）
     */
    List<ScoredChunk> rerank(String query, List<ScoredChunk> candidates, int topK);
}
