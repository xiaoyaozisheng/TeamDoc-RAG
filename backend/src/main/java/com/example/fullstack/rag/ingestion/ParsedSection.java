package com.example.fullstack.rag.ingestion;

import java.util.List;

/** 解析结果：按自然段落（尽量保留标题/章节边界）切分，并携带页码。 */
public record ParsedSection(String content, int pageNumber) {
    public static List<ParsedSection> single(String content) {
        return List.of(new ParsedSection(content, 1));
    }
}
