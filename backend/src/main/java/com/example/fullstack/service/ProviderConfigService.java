package com.example.fullstack.service;

import com.example.fullstack.config.RagProperties;
import com.example.fullstack.dto.ProviderConfigRequest;
import com.example.fullstack.entity.AppSetting;
import com.example.fullstack.repository.AppSettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * SYS-03：Embedding/LLM Provider 运行时配置。
 * 配置持久化到 app_settings 表，启动时恢复；修改即时生效（Provider 按调用解析）。
 * Embedding 侧变更（provider/model/dimensions）会使已入库向量失效，响应标记 reindexRequired=true，
 * 管理员需对各租户文档调用 POST /documents/{id}/reindex。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderConfigService {

    public static final String SETTING_KEY = "rag.providers";

    private static final Set<String> EMBEDDING_PROVIDERS = Set.of("hashing", "openai");
    private static final Set<String> LLM_PROVIDERS = Set.of("extractive", "openai");

    private final AppSettingRepository appSettingRepository;
    private final RagProperties ragProperties;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /** 启动时恢复持久化的 Provider 配置（覆盖 application.yml 默认值） */
    @PostConstruct
    public void restorePersistedConfig() {
        appSettingRepository.findBySettingKey(SETTING_KEY).ifPresent(setting -> {
            try {
                ProviderConfigRequest config = objectMapper.readValue(
                        setting.getSettingValue(), ProviderConfigRequest.class);
                applyToProperties(config);
                log.info("已恢复持久化的 Provider 配置（app_settings.rag.providers）");
            } catch (Exception e) {
                log.warn("恢复 Provider 配置失败，使用 application.yml 默认值", e);
            }
        });
    }

    /**
     * 更新 Provider 配置：校验 → 热更新 RagProperties → 持久化 → 审计。
     * 返回 reindexRequired 标记（Embedding 侧变更时为 true）。
     */
    @Transactional
    public Map<String, Object> update(ProviderConfigRequest request) {
        validate(request);
        boolean reindexRequired = applyToProperties(request);
        persist();
        auditService.log(null, null, "UPDATE_PROVIDER_CONFIG", summarize(request));
        log.info("Provider 配置已更新（reindexRequired={}）", reindexRequired);
        return Map.of("reindexRequired", reindexRequired);
    }

    /** 当前生效配置（apiKey 脱敏），供 GET /admin/providers 复用 */
    public Map<String, Object> currentConfig() {
        RagProperties.Embedding emb = ragProperties.getEmbedding();
        RagProperties.Llm llm = ragProperties.getLlm();
        return Map.of(
                "embedding", Map.of(
                        "provider", nvl(emb.getProvider()),
                        "dimensions", emb.getDimensions(),
                        "model", nvl(emb.getOpenai().getModel()),
                        "baseUrl", nvl(emb.getOpenai().getBaseUrl()),
                        "apiKeyMasked", mask(emb.getOpenai().getApiKey())),
                "llm", Map.of(
                        "provider", nvl(llm.getProvider()),
                        "model", nvl(llm.getOpenai().getModel()),
                        "baseUrl", nvl(llm.getOpenai().getBaseUrl()),
                        "apiKeyMasked", mask(llm.getOpenai().getApiKey())),
                "rerankerEnabled", ragProperties.getRetrieval().isRerankerEnabled());
    }

    /** 应用配置到运行时属性，返回是否发生 Embedding 侧变更（需要重索引） */
    private boolean applyToProperties(ProviderConfigRequest request) {
        boolean reindexRequired = false;
        if (request.getRerankerEnabled() != null) {
            ragProperties.getRetrieval().setRerankerEnabled(request.getRerankerEnabled());
        }
        if (request.getEmbedding() != null) {
            ProviderConfigRequest.EmbeddingConfig emb = request.getEmbedding();
            RagProperties.Embedding target = ragProperties.getEmbedding();
            if (isPresent(emb.getProvider()) && !emb.getProvider().equals(target.getProvider())) {
                target.setProvider(emb.getProvider());
                reindexRequired = true;
            }
            if (isPresent(emb.getBaseUrl())) {
                target.getOpenai().setBaseUrl(emb.getBaseUrl());
            }
            if (isPresent(emb.getModel())) {
                target.getOpenai().setModel(emb.getModel());
                reindexRequired = true;
            }
            if (isPresent(emb.getApiKey())) {
                target.getOpenai().setApiKey(emb.getApiKey());
            }
            if (emb.getDimensions() != null && emb.getDimensions() != target.getDimensions()) {
                target.setDimensions(emb.getDimensions());
                reindexRequired = true;
            }
        }
        if (request.getLlm() != null) {
            ProviderConfigRequest.LlmConfig llm = request.getLlm();
            RagProperties.Llm target = ragProperties.getLlm();
            if (isPresent(llm.getProvider())) {
                target.setProvider(llm.getProvider());
            }
            if (isPresent(llm.getBaseUrl())) {
                target.getOpenai().setBaseUrl(llm.getBaseUrl());
            }
            if (isPresent(llm.getModel())) {
                target.getOpenai().setModel(llm.getModel());
            }
            if (isPresent(llm.getApiKey())) {
                target.getOpenai().setApiKey(llm.getApiKey());
            }
            if (llm.getTimeoutSeconds() != null && llm.getTimeoutSeconds() > 0) {
                target.getOpenai().setTimeoutSeconds(llm.getTimeoutSeconds());
            }
        }
        return reindexRequired;
    }

    private void validate(ProviderConfigRequest request) {
        if (request.getEmbedding() == null && request.getLlm() == null && request.getRerankerEnabled() == null) {
            throw new IllegalArgumentException("至少提供 embedding、llm 或 rerankerEnabled 之一");
        }
        if (request.getEmbedding() != null) {
            String provider = request.getEmbedding().getProvider();
            if (isPresent(provider) && !EMBEDDING_PROVIDERS.contains(provider)) {
                throw new IllegalArgumentException("embedding.provider 仅支持: hashing | openai");
            }
        }
        if (request.getLlm() != null) {
            String provider = request.getLlm().getProvider();
            if (isPresent(provider) && !LLM_PROVIDERS.contains(provider)) {
                throw new IllegalArgumentException("llm.provider 仅支持: extractive | openai");
            }
            if (isPresent(request.getLlm().getProvider())
                    && "openai".equals(request.getLlm().getProvider())
                    && !isPresent(request.getLlm().getApiKey())
                    && !isPresent(ragProperties.getLlm().getOpenai().getApiKey())) {
                throw new IllegalArgumentException("切换 llm.provider=openai 需提供 apiKey");
            }
        }
    }

    private void persist() {
        try {
            // apiKey 不落库为 null/空串：与当前运行时值一致地序列化（运行时已合并新值）
            ProviderConfigRequest snapshot = ProviderConfigRequest.builder()
                    .rerankerEnabled(ragProperties.getRetrieval().isRerankerEnabled())
                    .embedding(ProviderConfigRequest.EmbeddingConfig.builder()
                            .provider(ragProperties.getEmbedding().getProvider())
                            .baseUrl(ragProperties.getEmbedding().getOpenai().getBaseUrl())
                            .model(ragProperties.getEmbedding().getOpenai().getModel())
                            .apiKey(ragProperties.getEmbedding().getOpenai().getApiKey())
                            .dimensions(ragProperties.getEmbedding().getDimensions())
                            .build())
                    .llm(ProviderConfigRequest.LlmConfig.builder()
                            .provider(ragProperties.getLlm().getProvider())
                            .baseUrl(ragProperties.getLlm().getOpenai().getBaseUrl())
                            .model(ragProperties.getLlm().getOpenai().getModel())
                            .apiKey(ragProperties.getLlm().getOpenai().getApiKey())
                            .timeoutSeconds(ragProperties.getLlm().getOpenai().getTimeoutSeconds())
                            .build())
                    .build();
            String json = objectMapper.writeValueAsString(snapshot);
            AppSetting setting = appSettingRepository.findBySettingKey(SETTING_KEY)
                    .orElseGet(() -> AppSetting.builder().settingKey(SETTING_KEY).build());
            setting.setSettingValue(json);
            appSettingRepository.save(setting);
        } catch (Exception e) {
            throw new IllegalStateException("Provider 配置持久化失败", e);
        }
    }

    private String summarize(ProviderConfigRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.getEmbedding() != null) {
            sb.append("embedding=").append(nvl(request.getEmbedding().getProvider())).append("; ");
        }
        if (request.getLlm() != null) {
            sb.append("llm=").append(nvl(request.getLlm().getProvider())).append("; ");
        }
        return sb.toString();
    }

    public String mask(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        return apiKey.length() <= 8
                ? "****"
                : apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
