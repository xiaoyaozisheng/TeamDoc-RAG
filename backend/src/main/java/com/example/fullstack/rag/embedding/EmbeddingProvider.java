package com.example.fullstack.rag.embedding;

import java.util.List;

/**
 * Embedding 供应商适配层：本地 hashing 起步，可替换为 bge ONNX / 云端 API，
 * 入库与检索流水线不感知具体实现。
 */
public interface EmbeddingProvider {

    /** 向量维度，须与 pgvector 的 vector(dim) 一致 */
    int dimensions();

    List<float[]> embedBatch(List<String> texts);

    default float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }
}
