package com.example.fullstack.controller;

import com.example.fullstack.config.RagProperties;
import com.example.fullstack.repository.AuditLogRepository;
import com.example.fullstack.repository.RetrievalLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 系统管理（SYS-03 ~ SYS-04）：检索日志、审计日志、Provider 配置查看。
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "系统管理", description = "日志与 Provider 配置（仅系统管理员）")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final RetrievalLogRepository retrievalLogRepository;
    private final AuditLogRepository auditLogRepository;
    private final RagProperties ragProperties;

    @Operation(summary = "检索日志（RET-05）：按租户/时间筛选")
    @GetMapping("/logs/retrieval")
    public ResponseEntity<List<Map<String, Object>>> retrievalLogs(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        LocalDateTime startTime = start == null ? LocalDateTime.now().minusDays(7) : LocalDateTime.parse(start);
        LocalDateTime endTime = end == null ? LocalDateTime.now() : LocalDateTime.parse(end);
        var pageable = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt"));

        var result = (tenantId == null)
                ? retrievalLogRepository.findAll(pageable)
                : retrievalLogRepository.findByTenantIdAndCreatedAtBetween(tenantId, startTime, endTime, pageable);

        List<Map<String, Object>> logs = result.getContent().stream()
                .map(log -> Map.<String, Object>of(
                        "id", log.getId(),
                        "tenantId", log.getTenant() == null ? -1 : log.getTenant().getId(),
                        "query", log.getQuery(),
                        "candidateCount", log.getCandidateCount(),
                        "latencyMs", log.getLatencyMs(),
                        "result", log.getResultJson() == null ? "" : log.getResultJson(),
                        "createdAt", log.getCreatedAt().toString()))
                .toList();
        return ResponseEntity.ok(logs);
    }

    @Operation(summary = "审计日志（SYS-04）：登录/权限变更/文档删除")
    @GetMapping("/logs/audit")
    public ResponseEntity<List<Map<String, Object>>> auditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Map<String, Object>> logs = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)
                .getContent().stream()
                .map(log -> Map.<String, Object>of(
                        "id", log.getId(),
                        "tenantId", log.getTenant() == null ? -1 : log.getTenant().getId(),
                        "userId", log.getUserId() == null ? -1 : log.getUserId(),
                        "action", log.getAction(),
                        "detail", log.getDetail() == null ? "" : log.getDetail(),
                        "createdAt", log.getCreatedAt().toString()))
                .toList();
        return ResponseEntity.ok(logs);
    }

    @Operation(summary = "查看 Provider 配置（SYS-03，密钥脱敏）")
    @GetMapping("/providers")
    public Map<String, Object> providers() {
        return Map.of(
                "embedding", Map.of(
                        "provider", ragProperties.getEmbedding().getProvider(),
                        "dimensions", ragProperties.getEmbedding().getDimensions(),
                        "model", ragProperties.getEmbedding().getOpenai().getModel(),
                        "apiKeyMasked", mask(ragProperties.getEmbedding().getOpenai().getApiKey())),
                "llm", Map.of(
                        "provider", ragProperties.getLlm().getProvider(),
                        "model", ragProperties.getLlm().getOpenai().getModel(),
                        "baseUrl", ragProperties.getLlm().getOpenai().getBaseUrl(),
                        "apiKeyMasked", mask(ragProperties.getLlm().getOpenai().getApiKey())));
    }

    private String mask(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        return apiKey.length() <= 8
                ? "****"
                : apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
