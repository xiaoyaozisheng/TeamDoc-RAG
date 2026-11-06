package com.example.fullstack.controller;

import com.example.fullstack.dto.RetrievalResult;
import com.example.fullstack.entity.User;
import com.example.fullstack.exception.TokenRefreshException;
import com.example.fullstack.rag.chat.ChatService;
import com.example.fullstack.rag.search.RetrievalService;
import com.example.fullstack.rag.search.ScoredChunk;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "RAG 问答", description = "基于证据的问答，带引用溯源与拒答（CHAT-01~07）")
@PreAuthorize("hasAuthority('chat:ask') or hasRole('ADMIN')")
public class ChatController {

    private final ChatService chatService;
    private final RetrievalService retrievalService;

    @Data
    public static class AskRequest {
        @NotBlank(message = "问题不能为空")
        private String question;
    }

    @Data
    public static class FeedbackRequest {
        @jakarta.validation.constraints.NotNull
        private Long conversationId;
        @NotBlank
        private String rating; // USEFUL | USELESS | WRONG_CITATION
        private String comment;
    }

    @Operation(summary = "问答（同步 JSON）")
    @PostMapping("/ask")
    public ChatService.ChatAnswer ask(@AuthenticationPrincipal User user,
                                      @Valid @RequestBody AskRequest request) {
        return chatService.ask(user.getTenant(), user, request.getQuestion());
    }

    @Operation(summary = "问答历史（CHAT-06）：当前用户的问答记录倒序分页")
    @GetMapping("/history")
    public List<ChatService.HistoryItem> history(@AuthenticationPrincipal User user,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return chatService.history(user.getTenant(), user, page, size);
    }

    @Operation(summary = "问答（SSE 流式，CHAT-01）：event=answer 增量 / citations 引用 / done 结束")
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@AuthenticationPrincipal User user,
                                @Valid @RequestBody AskRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        // 在请求线程（OSIV 存活）内捕获租户，避免跨线程访问懒加载代理
        com.example.fullstack.entity.Tenant tenant = user.getTenant();
        Long tenantId = tenant == null ? null : tenant.getId();
        var executor = Executors.newSingleThreadExecutor();
        emitter.onTimeout(emitter::complete);
        executor.execute(() -> {
            try {
                // SSE 异步线程没有请求上下文：手动注入租户上下文，RLS 会话变量随连接注入才不会报错
                if (tenantId != null) {
                    com.example.fullstack.security.TenantContextHolder.set(tenantId);
                }
                // 检索一次，引用先推、生成复用同一份命中（避免双倍检索与重复检索日志）
                List<ScoredChunk> hits = retrievalService.search(tenant, request.getQuestion());
                ChatService.ChatAnswer answer = chatService.ask(tenant, user, request.getQuestion(), hits);

                String text = answer.answer();
                int step = 24;
                for (int i = 0; i < text.length(); i += step) {
                    emitter.send(SseEmitter.event().name("answer")
                            .data(text.substring(i, Math.min(text.length(), i + step))));
                }
                emitter.send(SseEmitter.event().name("citations").data(answer.citations()));
                emitter.send(SseEmitter.event().name("done").data(Map.of(
                        "conversationId", answer.conversationId(),
                        "rejected", answer.rejected())));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            } finally {
                com.example.fullstack.security.TenantContextHolder.clear();
                executor.shutdown();
            }
        });
        return emitter;
    }

    @Operation(summary = "回答反馈（CHAT-06）：USEFUL / USELESS / WRONG_CITATION")
    @PostMapping("/feedback")
    public Map<String, Object> feedback(@AuthenticationPrincipal User user,
                                        @Valid @RequestBody FeedbackRequest request) {
        chatService.feedback(user.getTenant(), request.getConversationId(), request.getRating(), request.getComment());
        return Map.of("ok", true);
    }

    @Operation(summary = "检索预览（问答前查看命中的片段）")
    @PostMapping("/context")
    public List<RetrievalResult.Hit> context(@AuthenticationPrincipal User user,
                                             @Valid @RequestBody AskRequest request) {
        List<ScoredChunk> hits = retrievalService.search(user.getTenant(), request.getQuestion());
        return hits.stream()
                .map(hit -> RetrievalResult.Hit.builder()
                        .chunkId(hit.chunkId())
                        .documentId(hit.documentId())
                        .documentName(hit.documentName())
                        .pageNumber(hit.pageNumber())
                        .content(hit.content())
                        .rrfScore(hit.rrfScore())
                        .build())
                .toList();
    }
}
