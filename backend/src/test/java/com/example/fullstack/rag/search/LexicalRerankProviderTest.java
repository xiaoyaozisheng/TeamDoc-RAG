package com.example.fullstack.rag.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** LexicalRerankProvider 纯函数单测（RET-07） */
class LexicalRerankProviderTest {

    private final LexicalRerankProvider reranker = new LexicalRerankProvider();

    private ScoredChunk chunk(long id, String content, double rrf) {
        return new ScoredChunk(id, 100L + id, "文档" + id, 1, content, 0.5, 0.2, rrf);
    }

    @Test
    void rerank_shouldPromoteLexicallyRelevantCandidate() {
        // RRF 排名把弱相关片段排第一，词面重排应把真正含"报销流程"的片段提升到首位
        List<ScoredChunk> candidates = List.of(
                chunk(1, "今天天气不错，适合外出郊游。", 0.05),
                chunk(2, "员工报销流程：先填写报销单，提交部门审批。", 0.03),
                chunk(3, "公司年会在十二月举办。", 0.02));

        List<ScoredChunk> result = reranker.rerank("报销流程是什么", candidates, 2);

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).chunkId(), "词面相关的片段应排第一");
    }

    @Test
    void rerank_shouldKeepOrder_whenCandidatesEmpty() {
        assertTrue(reranker.rerank("任意", List.of(), 5).isEmpty());
        assertTrue(reranker.rerank("", null, 5).isEmpty());
    }

    @Test
    void rerank_shouldLimitToTopK() {
        List<ScoredChunk> candidates = List.of(
                chunk(1, "报销流程第一步", 0.05),
                chunk(2, "报销流程第二步", 0.04),
                chunk(3, "报销流程第三步", 0.03));

        assertEquals(1, reranker.rerank("报销流程", candidates, 1).size());
        assertEquals(3, reranker.rerank("报销流程", candidates, 10).size());
    }

    @Test
    void rerank_shouldPreserveMetadataAndScores() {
        ScoredChunk original = new ScoredChunk(7L, 42L, "产品手册", 12, "产品X的型号编码是DX-100", 0.81, 0.13, 0.05);
        List<ScoredChunk> result = reranker.rerank("产品X 型号", List.of(original), 5);

        assertEquals(1, result.size());
        ScoredChunk reranked = result.get(0);
        assertEquals(7L, reranked.chunkId());
        assertEquals(42L, reranked.documentId());
        assertEquals("产品手册", reranked.documentName());
        assertEquals(12, reranked.pageNumber());
        assertEquals(0.81, reranked.vectorScore());
        assertEquals(0.13, reranked.keywordScore());
        assertTrue(reranked.rrfScore() > 0, "重排后应有融合后的最终得分");
    }

    @Test
    void bigrams_shouldHandleSingleCharAndNull() {
        assertTrue(LexicalRerankProvider.bigrams(null).isEmpty());
        assertTrue(LexicalRerankProvider.bigrams("  ").isEmpty());
        assertEquals(java.util.Set.of("a"), LexicalRerankProvider.bigrams("a"));
        assertEquals(java.util.Set.of("报销"), LexicalRerankProvider.bigrams("报销"));
    }
}
