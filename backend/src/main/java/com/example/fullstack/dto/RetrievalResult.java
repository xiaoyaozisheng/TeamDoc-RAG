package com.example.fullstack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalResult {
    private String query;
    private List<Hit> hits;
    private Long latencyMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Hit {
        private Long chunkId;
        private Long documentId;
        private String documentName;
        private Integer pageNumber;
        private String content;
        private Double vectorScore;
        private Double keywordScore;
        private Double rrfScore;
    }
}
