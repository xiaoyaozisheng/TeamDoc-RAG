package com.example.fullstack.controller;

import com.example.fullstack.entity.EvalQuestion;
import com.example.fullstack.entity.EvalRun;
import com.example.fullstack.entity.User;
import com.example.fullstack.rag.eval.EvalService;
import com.example.fullstack.repository.EvalQuestionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/eval")
@RequiredArgsConstructor
@Tag(name = "评估", description = "黄金评估集管理与批量评估（EVA-01~04）")
@PreAuthorize("hasAuthority('eval:manage') or hasRole('ADMIN')")
public class EvalController {

    private final EvalQuestionRepository evalQuestionRepository;
    private final EvalService evalService;
    private final ObjectMapper objectMapper;

    @Data
    public static class EvalQuestionRequest {
        @NotBlank(message = "问题不能为空")
        private String question;
        private String expectedAnswer;
        private Long expectedDocId;
        private Boolean negative = false;
    }

    @Operation(summary = "评估问题列表")
    @GetMapping("/questions")
    public List<Map<String, Object>> listQuestions(@AuthenticationPrincipal User user) {
        return evalQuestionRepository.findByTenantId(user.getTenant().getId()).stream()
                .map(this::toMap)
                .toList();
    }

    @Operation(summary = "新增评估问题（含负样本）")
    @PostMapping("/questions")
    public Map<String, Object> addQuestion(@AuthenticationPrincipal User user,
                                           @Valid @RequestBody EvalQuestionRequest request) {
        EvalQuestion saved = evalQuestionRepository.save(EvalQuestion.builder()
                .tenant(user.getTenant())
                .question(request.getQuestion())
                .expectedAnswer(request.getExpectedAnswer())
                .expectedDocId(request.getExpectedDocId())
                .negative(Boolean.TRUE.equals(request.getNegative()))
                .build());
        return toMap(saved);
    }

    @Operation(summary = "删除评估问题")
    @DeleteMapping("/questions/{id}")
    public Map<String, Object> deleteQuestion(@AuthenticationPrincipal User user, @PathVariable Long id) {
        EvalQuestion question = evalQuestionRepository.findById(id)
                .filter(q -> q.getTenant().getId().equals(user.getTenant().getId()))
                .orElseThrow(() -> new com.example.fullstack.exception.ResourceNotFoundException("评估问题不存在"));
        evalQuestionRepository.delete(question);
        return Map.of("ok", true);
    }

    @Operation(summary = "批量跑评估：Recall@5 + 负样本拒答准确率")
    @PostMapping("/runs")
    public Map<String, Object> runEval(@AuthenticationPrincipal User user) {
        return evalService.run(user.getTenant());
    }

    @Operation(summary = "评估历史（结果可对比，EVA-02）")
    @GetMapping("/runs")
    public List<Map<String, Object>> runs(@AuthenticationPrincipal User user) {
        return evalService.history(user.getTenant()).stream()
                .map(run -> {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> metrics = objectMapper.readValue(run.getMetricsJson(), Map.class);
                        return Map.<String, Object>of(
                                "id", run.getId(),
                                "questionCount", run.getQuestionCount(),
                                "createdAt", run.getCreatedAt().toString(),
                                "metrics", metrics);
                    } catch (Exception e) {
                        return Map.<String, Object>of("id", run.getId(), "error", "metrics 解析失败");
                    }
                })
                .toList();
    }

    private Map<String, Object> toMap(EvalQuestion question) {
        return Map.of(
                "id", question.getId(),
                "question", question.getQuestion(),
                "expectedAnswer", question.getExpectedAnswer() == null ? "" : question.getExpectedAnswer(),
                "expectedDocId", question.getExpectedDocId() == null ? -1 : question.getExpectedDocId(),
                "negative", Boolean.TRUE.equals(question.getNegative()));
    }
}
