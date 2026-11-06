package com.example.fullstack.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rag")
@Data
public class RagProperties {

    private Storage storage = new Storage();
    private Embedding embedding = new Embedding();
    private Llm llm = new Llm();
    private Chunk chunk = new Chunk();
    private Retrieval retrieval = new Retrieval();
    private Pgvector pgvector = new Pgvector();
    private Rls rls = new Rls();
    private Ingestion ingestion = new Ingestion();
    /** h2（LIKE 兜底）| pg（tsvector 全文检索） */
    private String keywordEngine = "h2";

    @Data
    public static class Storage {
        private String baseDir = "./data/files";
    }

    @Data
    public static class Embedding {
        /** hashing（本地确定性，零依赖）| openai */
        private String provider = "hashing";
        private int dimensions = 768;
        private OpenAi openai = new OpenAi();

        @Data
        public static class OpenAi {
            private String baseUrl = "https://api.openai.com";
            private String model = "text-embedding-3-small";
            private String apiKey = "";
        }
    }

    @Data
    public static class Llm {
        /** extractive（离线兜底）| openai */
        private String provider = "extractive";
        private OpenAi openai = new OpenAi();

        @Data
        public static class OpenAi {
            private String baseUrl = "https://api.openai.com";
            private String model = "gpt-4o-mini";
            private String apiKey = "";
            private int timeoutSeconds = 60;
        }
    }

    @Data
    public static class Chunk {
        private int maxParentChars = 1200;
        private int maxChildChars = 400;
    }

    @Data
    public static class Retrieval {
        private int topK = 5;
        private int rrfK = 60;
        private int vectorCandidates = 20;
        private int keywordCandidates = 20;
        /** 证据充分性阈值（向量余弦相似度）：低于该值拒答（CHAT-04） */
        private double minRelevance = 0.35;
        /** RET-07：重排开关（默认关闭；开启后对 Top N 重排取 TopK） */
        private boolean rerankerEnabled = false;
        /** 送入重排的候选数（RRF 融合先取这么多，再重排取 topK） */
        private int rerankCandidates = 20;
    }

    @Data
    public static class Pgvector {
        private boolean enabled = false;
    }

    @Data
    public static class Rls {
        private boolean enabled = false;
    }

    @Data
    public static class Ingestion {
        private int maxRetries = 3;
        private long scanIntervalMs = 3000;
    }
}
