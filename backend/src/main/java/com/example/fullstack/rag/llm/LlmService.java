package com.example.fullstack.rag.llm;

import com.example.fullstack.config.RagProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LlmService {

    private final ExtractiveLlmProvider extractiveProvider;
    private final OpenAiLlmProvider openAiProvider;
    private final RagProperties properties;

    public LlmProvider provider() {
        return switch (properties.getLlm().getProvider()) {
            case "openai" -> openAiProvider;
            default -> extractiveProvider;
        };
    }
}
