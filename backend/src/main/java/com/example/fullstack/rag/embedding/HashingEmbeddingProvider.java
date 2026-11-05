package com.example.fullstack.rag.embedding;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地确定性 Embedding：字符 bigram 特征哈希 + L2 归一化。
 * 零依赖、零网络，适合 MVP 与测试；中文场景召回质量对切片依赖大，
 * 生产环境通过替换 EmbeddingProvider 接入 bge-small-zh ONNX 或云端模型。
 */
@Component
public class HashingEmbeddingProvider implements EmbeddingProvider {

    private final int dimensions;

    public HashingEmbeddingProvider(com.example.fullstack.config.RagProperties properties) {
        this.dimensions = properties.getEmbedding().getDimensions();
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> result = new ArrayList<>(texts.size());
        for (String text : texts) {
            result.add(embedOne(text));
        }
        return result;
    }

    private float[] embedOne(String text) {
        float[] vector = new float[dimensions];
        if (text == null || text.isBlank()) {
            return vector;
        }
        String normalized = text.toLowerCase().replaceAll("\\s+", " ").trim();
        Map<Integer, Float> weights = new HashMap<>();

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            accumulate(weights, String.valueOf(c), 1.0f);
            if (i + 1 < normalized.length()) {
                accumulate(weights, normalized.substring(i, i + 2), 1.5f);
            }
        }

        weights.forEach((index, weight) -> vector[index] = weight);

        float norm = 0f;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }

    private void accumulate(Map<Integer, Float> weights, String gram, float weight) {
        int hash = gram.hashCode();
        int index = Math.floorMod(hash, dimensions);
        // 双哈希减少碰撞方向性
        int sign = Math.floorMod(hash >>> 16, 2) == 0 ? 1 : -1;
        weights.merge(index, weight * sign, Float::sum);
    }
}
