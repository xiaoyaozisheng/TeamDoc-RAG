package com.example.fullstack.rag.llm;

import com.example.fullstack.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Chat 实现。仅在 rag.llm.provider=openai 且配置了 API Key 时启用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiLlmProvider implements LlmProvider {

    private final RagProperties properties;

    private RestClient client;

    @Override
    public String name() {
        return "openai";
    }

    private RestClient client() {
        if (client == null) {
            RagProperties.Llm.OpenAi cfg = properties.getLlm().getOpenai();
            this.client = RestClient.builder()
                    .baseUrl(cfg.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + cfg.getApiKey())
                    .build();
        }
        return client;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String complete(String systemPrompt, String userPrompt) {
        RagProperties.Llm.OpenAi cfg = properties.getLlm().getOpenai();
        try {
            Map<String, Object> request = Map.of(
                    "model", cfg.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", 0.1
            );
            Map<String, Object> response = client().post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            throw new LlmException("LLM 调用失败: " + e.getMessage(), e);
        }
    }
}
