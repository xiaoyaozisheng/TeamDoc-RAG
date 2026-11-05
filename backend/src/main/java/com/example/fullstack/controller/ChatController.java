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

    @Operation(summary = "问答（SSE 流式，CHAT-01）：event=answer 增量 / citations 引用 / done 结束")
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@AuthenticationPrincipal User user,
                                @Valid @RequestBody AskRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        var executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                // 检索先行，先推送引用让前端可渲染来源
                List<ScoredChunk> hits = retrievalService.search(user.getTenant(), request.getQuestion());
                ChatService.ChatAnswer answer = chatService.ask(user.getTenant(), user, request.getQuestion());

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
