package com.example.fullstack.rag.search;

/**
 * 检索命中的子块，附带两路原始得分与 RRF 融合得分。
 */
public record ScoredChunk(
        Long chunkId,
        Long documentId,
        String documentName,
        int pageNumber,
        String content,
        Double vectorScore,
        Double keywordScore,
        double rrfScore
) {}
