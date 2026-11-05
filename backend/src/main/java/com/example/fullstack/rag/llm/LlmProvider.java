package com.example.fullstack.rag.llm;

/**
 * LLM 供应商适配层：本地抽取式兜底与云端 API 实现同一接口，按问题路由可后续扩展。
 */
public interface LlmProvider {

    String name();

    /**
     * @param systemPrompt 系统提示词（证据约束、引用格式、拒答规则）
     * @param userPrompt   用户提示词（问题 + 编号片段）
     * @return 模型输出文本；失败时抛出 LlmException
     */
    String complete(String systemPrompt, String userPrompt);
}
