package com.example.fullstack.rag.chat;

import com.example.fullstack.config.RagProperties;
import com.example.fullstack.entity.Conversation;
import com.example.fullstack.entity.Tenant;
import com.example.fullstack.entity.User;
import com.example.fullstack.rag.llm.LlmService;
import com.example.fullstack.rag.search.RetrievalService;
import com.example.fullstack.rag.search.ScoredChunk;
import com.example.fullstack.repository.ConversationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于证据的 RAG 问答（CHAT-01 ~ CHAT-07）：
 * 检索 -> 相关度阈值判断（不足即拒答） -> 证据约束 Prompt（强制引用编号） -> 生成 -> 记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    public static final String NO_ANSWER = "资料中未找到";

    /** 单个片段进入 Prompt 的最大字符数（CHAT-07：限制上下文长度） */
    private static final int MAX_CONTEXT_CHARS_PER_CHUNK = 1200;

    private static final String SYSTEM_PROMPT = """
            你是团队知识库问答助手。严格遵守以下规则：
            1. 仅基于提供的编号片段回答，禁止引入片段之外的知识；
            2. 每个结论后必须标注来源片段编号，如 [1]、[2]；
            3. 如果片段中没有足够信息回答问题，只回复：%s；
            4. 不要编造数字、名称或条款。
            """.formatted(NO_ANSWER);

    private final RetrievalService retrievalService;
    private final LlmService llmService;
    private final ConversationRepository conversationRepository;
    private final RagProperties properties;
    private final ObjectMapper objectMapper;

    public ChatAnswer ask(Tenant tenant, User user, String question) {
        List<ScoredChunk> hits = retrievalService.search(tenant, question);

        // 证据不足拒答（CHAT-04）：RRF 是排名型分数，不反映相关度绝对值，
        // 证据充分性用向量余弦相似度判据（两路都无命中时必然为 0）
        double bestRelevance = hits.stream()
                .mapToDouble(hit -> hit.vectorScore() == null ? 0.0 : hit.vectorScore())
                .max()
                .orElse(0.0);
        double minRelevance = properties.getRetrieval().getMinRelevance();
        if (hits.isEmpty() || bestRelevance < minRelevance) {
            log.info("拒答: question={}, bestRelevance={}, minRelevance={}", question, bestRelevance, minRelevance);
            Conversation conversation = save(tenant, user, question, NO_ANSWER, List.of());
            return new ChatAnswer(conversation.getId(), NO_ANSWER, List.of(), true);
        }

        String userPrompt = buildPrompt(question, hits, retrievalService.loadParentContents(hits));
        String answer;
        try {
            answer = llmService.provider().complete(SYSTEM_PROMPT, userPrompt);
        } catch (Exception e) {
            log.error("LLM 调用失败，降级为拒答", e);
            answer = NO_ANSWER;
        }

        if (answer == null || answer.isBlank()) {
            answer = NO_ANSWER;
        }

        List<Citation> citations = buildCitations(hits, answer);
        boolean rejected = answer.contains(NO_ANSWER);
        Conversation conversation = save(tenant, user, question, answer, citations);
        return new ChatAnswer(conversation.getId(), answer, citations, rejected);
    }

    private String buildPrompt(String question, List<ScoredChunk> hits, Map<Long, String> parentContents) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("问题：").append(question.strip()).append("\n\n片段：\n");
        for (int i = 0; i < hits.size(); i++) {
            ScoredChunk hit = hits.get(i);
            // ING-03 父子块：子块用于检索定位，父块提供完整章节上下文；无父块时退回子块
            String content = parentContents.getOrDefault(hit.chunkId(), hit.content());
            if (content.length() > MAX_CONTEXT_CHARS_PER_CHUNK) {
                content = content.substring(0, MAX_CONTEXT_CHARS_PER_CHUNK);
            }
            prompt.append('[').append(i + 1).append("] ")
                    .append(content.strip())
                    .append("（来源：").append(hit.documentName())
                    .append(" 第").append(hit.pageNumber()).append("页）\n");
        }
        prompt.append("\n请仅基于以上片段回答问题，并标注引用编号。");
        return prompt.toString();
    }

    /** 引用（CHAT-02）：LLM 输出中出现的编号才进入引用列表，保证引用准确率 */
    private List<Citation> buildCitations(List<ScoredChunk> hits, String answer) {
        List<Citation> citations = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            if (answer.contains("[" + (i + 1) + "]")) {
                ScoredChunk hit = hits.get(i);
                citations.add(new Citation(
                        hit.chunkId(), hit.documentId(), hit.documentName(), hit.pageNumber(), i + 1));
            }
        }
        return citations;
    }

    private Conversation save(Tenant tenant, User user, String question,
                              String answer, List<Citation> citations) {
        try {
            return conversationRepository.save(Conversation.builder()
                    .tenant(tenant)
                    .user(user)
                    .question(question)
                    .answer(answer)
                    .citations(objectMapper.writeValueAsString(citations))
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("问答记录保存失败", e);
        }
    }

    public record Citation(Long chunkId, Long documentId, String documentName,
                           int pageNumber, int index) {}

    public record ChatAnswer(Long conversationId, String answer, List<Citation> citations, boolean rejected) {}

    public void feedback(Tenant tenant, Long conversationId, String rating, String comment) {
        Conversation conversation = conversationRepository.findByIdAndTenantId(conversationId, tenant.getId())
                .orElseThrow(() -> new com.example.fullstack.exception.ResourceNotFoundException("问答记录不存在"));
        conversation.setRating(Conversation.Feedback.valueOf(rating));
        conversation.setFeedbackComment(comment);
        conversationRepository.save(conversation);
    }
}
