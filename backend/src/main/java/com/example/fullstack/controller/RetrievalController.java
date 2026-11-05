package com.example.fullstack.controller;

import com.example.fullstack.dto.RetrievalResult;
import com.example.fullstack.entity.User;
import com.example.fullstack.rag.search.RetrievalService;
import com.example.fullstack.rag.search.ScoredChunk;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/retrieval")
@RequiredArgsConstructor
@Tag(name = "检索", description = "混合检索（向量 + 全文 + RRF）调试入口")
public class RetrievalController {

    private final RetrievalService retrievalService;

    @Data
    public static class SearchRequest {
        @NotBlank(message = "查询不能为空")
        private String query;
    }

    @Operation(summary = "检索（RET-01~04）：返回片段、文档名、页码与各路得分")
    @PreAuthorize("hasAuthority('chat:ask') or hasAuthority('document:read') or hasRole('ADMIN')")
    @PostMapping("/search")
    public RetrievalResult search(@AuthenticationPrincipal User user, @jakarta.validation.Valid @RequestBody SearchRequest request) {
        long start = System.currentTimeMillis();
        List<ScoredChunk> hits = retrievalService.search(user.getTenant(), request.getQuery());
        return RetrievalResult.builder()
                .query(request.getQuery())
                .latencyMs(System.currentTimeMillis() - start)
                .hits(hits.stream()
                        .map(hit -> RetrievalResult.Hit.builder()
                                .chunkId(hit.chunkId())
                                .documentId(hit.documentId())
                                .documentName(hit.documentName())
                                .pageNumber(hit.pageNumber())
                                .content(hit.content())
                                .vectorScore(hit.vectorScore())
                                .keywordScore(hit.keywordScore())
                                .rrfScore(hit.rrfScore())
                                .build())
                        .toList())
                .build();
    }
}
