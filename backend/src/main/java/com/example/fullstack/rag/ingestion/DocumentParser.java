package com.example.fullstack.rag.ingestion;

import com.example.fullstack.entity.Document;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档解析器（ING-01）：PDF 按页解析保留页码；DOCX 提取正文段落；
 * Markdown 优先按标题行切分（ING-02：避免固定字数硬切撕碎表格/条款）；
 * TXT 按空行分段。
 */
@Slf4j
@Component
public class DocumentParser {

    public List<ParsedSection> parse(Document.DocType type, InputStream content) {
        try {
            return switch (type) {
                case PDF -> parsePdf(content);
                case DOCX -> parseDocx(content);
                case MD -> parseMarkdown(content);
                case TXT -> parseText(content);
            };
        } catch (IOException e) {
            throw new IllegalStateException("文档解析失败: " + e.getMessage(), e);
        }
    }

    private List<ParsedSection> parsePdf(InputStream content) throws IOException {
        List<ParsedSection> sections = new ArrayList<>();
        byte[] bytes = content.readAllBytes();
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pdf).strip();
                if (!text.isEmpty()) {
                    sections.add(new ParsedSection(text, page));
                }
            }
        }
        return sections;
    }

    private List<ParsedSection> parseDocx(InputStream content) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(content);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            String text = extractor.getText();
            return splitParagraphs(text, 1);
        }
    }

    private List<ParsedSection> parseMarkdown(InputStream content) throws IOException {
        String text = new String(content.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        List<ParsedSection> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\\R", -1)) {
            boolean isHeading = line.startsWith("#");
            if (isHeading && !current.isEmpty()) {
                addIfNotEmpty(sections, current.toString(), 1);
                current = new StringBuilder();
            }
            current.append(line).append('\n');
        }
        addIfNotEmpty(sections, current.toString(), 1);
        return sections;
    }

    private List<ParsedSection> parseText(InputStream content) throws IOException {
        String text = new String(content.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        return splitParagraphs(text, 1);
    }

    private List<ParsedSection> splitParagraphs(String text, int pageNumber) {
        List<ParsedSection> sections = new ArrayList<>();
        for (String paragraph : text.split("\\n\\s*\\n")) {
            addIfNotEmpty(sections, paragraph, pageNumber);
        }
        return sections;
    }

    private void addIfNotEmpty(List<ParsedSection> sections, String content, int pageNumber) {
        String stripped = content.strip();
        if (!stripped.isEmpty()) {
            sections.add(new ParsedSection(stripped, pageNumber));
        }
    }
}
