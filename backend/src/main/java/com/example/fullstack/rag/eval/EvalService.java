package com.example.fullstack.rag.eval;

import com.example.fullstack.config.RagProperties;
import com.example.fullstack.entity.EvalQuestion;
import com.example.fullstack.entity.EvalRun;
import com.example.fullstack.entity.Tenant;
import com.example.fullstack.exception.ResourceNotFoundException;
import com.example.fullstack.rag.search.RetrievalService;
import com.example.fullstack.rag.search.ScoredChunk;
import com.example.fullstack.repository.EvalQuestionRepository;
import com.example.fullstack.repository.EvalRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量评估（EVA-01 ~ EVA-04）：检索层指标驱动优化，
 * Recall 未达标先修切片与检索，不动 Prompt（PRD 里程碑纪律）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalService {

    private final EvalQuestionRepository evalQuestionRepository;
    private final EvalRunRepository evalRunRepository;
    private final RetrievalService retrievalService;
    private final RagProperties properties;
    private final ObjectMapper objectMapper;

    public Map<String, Object> run(Tenant tenant) {
        List<EvalQuestion> questions = evalQuestionRepository.findByTenantId(tenant.getId());
        if (questions.isEmpty()) {
            throw new ResourceNotFoundException("评估集为空，请先录入评估问题");
        }

        int positiveCount = 0;
        int recallHits = 0;
        int negativeCount = 0;
        int correctRejections = 0;
        double minRelevance = properties.getRetrieval().getMinRelevance();
        List<Map<String, Object>> details = new java.util.ArrayList<>();

        for (EvalQuestion question : questions) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("questionId", question.getId());
            detail.put("question", question.getQuestion());

            if (Boolean.TRUE.equals(question.getNegative())) {
                // 负样本：期望无强相关证据（判拒答），用向量余弦相似度做证据充分性判据
                negativeCount++;
                List<ScoredChunk> hits = retrievalService.search(tenant, question.getQuestion());
                double bestRelevance = hits.stream()
                        .mapToDouble(hit -> hit.vectorScore() == null ? 0.0 : hit.vectorScore())
                        .max()
                        .orElse(0.0);
                boolean rejected = hits.isEmpty() || bestRelevance < minRelevance;
                if (rejected) {
                    correctRejections++;
                }
                detail.put("negative", true);
                detail.put("rejected", rejected);
                detail.put("bestRelevance", bestRelevance);
            } else {
                // 正样本：期望文档出现在 Top5（Recall@5）
                positiveCount++;
                List<ScoredChunk> hits = retrievalService.search(tenant, question.getQuestion());
                boolean hit = hits.stream()
                        .limit(properties.getRetrieval().getTopK())
                        .anyMatch(h -> h.documentId().equals(question.getExpectedDocId()));
                if (hit) {
                    recallHits++;
                }
                detail.put("negative", false);
                detail.put("hit", hit);
                detail.put("topDocuments", hits.stream().limit(5)
                        .map(ScoredChunk::documentName).toList());
            }
            details.add(detail);
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("questionCount", questions.size());
        metrics.put("positiveCount", positiveCount);
        metrics.put("recallAt5", positiveCount == 0 ? 0.0 : (double) recallHits / positiveCount);
        metrics.put("negativeCount", negativeCount);
        metrics.put("rejectionAccuracy", negativeCount == 0 ? 0.0 : (double) correctRejections / negativeCount);
        metrics.put("details", details);

        try {
            evalRunRepository.save(EvalRun.builder()
                    .tenant(tenant)
                    .metricsJson(objectMapper.writeValueAsString(metrics))
                    .questionCount(questions.size())
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("评估结果保存失败", e);
        }
        return metrics;
    }

    public List<EvalRun> history(Tenant tenant) {
        return evalRunRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId());
    }
}
