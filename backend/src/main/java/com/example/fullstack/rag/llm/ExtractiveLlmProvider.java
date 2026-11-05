package com.example.fullstack.rag.llm;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 离线抽取式兜底实现：不调用任何外部模型。
 * 在检索命中的片段内挑选与问题重叠度最高的句子作为回答（带引用编号），
 * 保证"仅基于片段回答、引用可溯源、无证据拒答"三条底线在无 LLM 时依然成立。
 * 生产环境通过 rag.llm.provider=openai 切换到真实模型。
 */
@Component
public class ExtractiveLlmProvider implements LlmProvider {

    @Override
    public String name() {
        return "extractive";
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        // userPrompt 由 ChatService 构造：
        //   问题：<question>\n\n片段：\n[1] <content>（来源：...）\n[2] ...
        String question = extractQuestion(userPrompt);
        List<String> queryTerms = extractTerms(question);

        String bestSentence = null;
        int bestScore = -1;
        int bestIndex = 1;

        int chunkStart = userPrompt.indexOf("\n[1] ");
        int scanFrom = chunkStart < 0 ? 0 : chunkStart;
        int index = 0;
        while (true) {
            index++;
            String marker = "\n[" + index + "] ";
            int start = userPrompt.indexOf(marker, scanFrom);
            if (start < 0) {
                break;
            }
            int contentStart = start + marker.length();
            int contentEnd = userPrompt.indexOf("\n[", contentStart);
            String chunk = (contentEnd > contentStart
                    ? userPrompt.substring(contentStart, contentEnd)
                    : userPrompt.substring(contentStart)).strip();

            for (String sentence : chunk.split("(?<=[。！？!?])\\s*")) {
                String cleaned = sentence.replaceAll("（来源：[^）]*）$", "").strip();
                // 剥离粘连的 Markdown 标题行；纯标题句则跳过
                cleaned = cleaned.replaceAll("^(?:#[^\\n]*\\n)+", "").strip();
                if (cleaned.isEmpty() || cleaned.startsWith("#")) {
                    continue;
                }
                int score = 0;
                for (String term : queryTerms) {
                    if (cleaned.contains(term)) {
                        score += term.length();
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestSentence = cleaned;
                    bestIndex = index;
                }
            }
            scanFrom = contentStart;
        }

        if (bestSentence == null || bestScore <= 0) {
            // 无句子级命中时回退为最高片段首句，保持引用可溯源
            return null;
        }
        return bestSentence + " [" + bestIndex + "]";
    }

    private String extractQuestion(String userPrompt) {
        int questionStart = userPrompt.indexOf("问题：");
        int questionEnd = userPrompt.indexOf("\n", questionStart);
        return questionStart >= 0 && questionEnd > questionStart
                ? userPrompt.substring(questionStart + "问题：".length(), questionEnd)
                : userPrompt;
    }

    private List<String> extractTerms(String question) {
        String compact = question.replaceAll("[？?！!。.，,\\s]", "");
        List<String> terms = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < compact.length(); i++) {
            terms.add(compact.substring(i, i + 2));
        }
        if (terms.isEmpty() && !compact.isEmpty()) {
            terms.add(compact);
        }
        return terms;
    }
}
