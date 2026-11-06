package com.example.fullstack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SYS-03 Provider 运行时配置（PUT /admin/providers）。
 * 语义：null/空白字段表示"保持当前值不变"；apiKey 传新值即更新（响应只回脱敏值）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderConfigRequest {

    private EmbeddingConfig embedding;
    private LlmConfig llm;
    /** RET-07：检索重排开关；null 保持不变 */
    private Boolean rerankerEnabled;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbeddingConfig {
        /** hashing（本地确定性）| openai；null 保持不变 */
        private String provider;
        private String baseUrl;
        private String model;
        /** null/空白保持不变；传新值即更新（存库，接口只回脱敏） */
        private String apiKey;
        /** 向量维度；变更会使已入库向量失效，响应会标记 reindexRequired=true */
        private Integer dimensions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LlmConfig {
        /** extractive（离线兜底）| openai；null 保持不变 */
        private String provider;
        private String baseUrl;
        private String model;
        private String apiKey;
        private Integer timeoutSeconds;
    }
}
