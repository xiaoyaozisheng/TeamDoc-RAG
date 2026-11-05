package com.example.fullstack.rag.ingestion;

import com.example.fullstack.config.RagProperties;
import com.example.fullstack.entity.Chunk;
import com.example.fullstack.entity.Document;
import com.example.fullstack.rag.embedding.EmbeddingService;
import com.example.fullstack.rag.vector.PgVectorStoreWriter;
import com.example.fullstack.repository.ChunkRepository;
import com.example.fullstack.repository.DocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 入库流水线（ING-01 ~ ING-06）：
 * 解析 -> 父块（章节/段落级，供 LLM） -> 子块（细粒度，供检索） -> Embedding -> 落库（+ pgvector）。
 * 幂等：处理前先清除该文档既有切块；失败标记 FAILED 并记录原因，由 Worker 限量重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final DocumentParser documentParser;
    private final EmbeddingService embeddingService;
    private final LocalStorageService storageService;
    /** 仅 postgres Profile（rag.pgvector.enabled=true）下存在 */
    private final org.springframework.beans.factory.ObjectProvider<PgVectorStoreWriter> pgVectorStoreWriter;
    private final RagProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 非事务方法：状态流转与切块写入分开提交，失败置 FAILED 便于 Worker 重试；
     * 删除旧切块先于重建，重试天然幂等。
     */
    public void processDocument(Long documentId) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
            return;
        }
        if (document.getStatus() == Document.DocStatus.PROCESSING) {
            return;
        }

        document.setStatus(Document.DocStatus.PROCESSING);
        document.setErrorMsg(null);
        documentRepository.save(document);

        try {
            ingest(document);
            document.setStatus(Document.DocStatus.SUCCESS);
            documentRepository.save(document);
            log.info("文档入库成功: {} ({} 个子块)", document.getName(), documentId);
        } catch (Exception e) {
            log.error("文档入库失败: {}", document.getName(), e);
            document.setStatus(Document.DocStatus.FAILED);
            document.setErrorMsg(abbreviate(e.getMessage()));
            document.setRetryCount(document.getRetryCount() == null ? 1 : document.getRetryCount() + 1);
            documentRepository.save(document);
        }
    }

    private void ingest(Document document) throws Exception {
        // 幂等（ING-06）：重复执行不产生重复向量
        chunkRepository.deleteByDocumentId(document.getId());
        chunkRepository.flush();

        List<ParsedSection> sections;
        try (InputStream in = storageService.read(document.getStorageKey())) {
            sections = documentParser.parse(document.getType(), in);
        }
        if (sections.isEmpty()) {
            throw new IllegalStateException("文档解析结果为空");
        }

        // 父块：章节/段落合并到 maxParentChars，保证价格表、条款等段落不被撕碎
        List<ParentBlock> parents = buildParents(sections);

        List<Chunk> parentRows = new ArrayList<>(parents.size());
        for (ParentBlock parent : parents) {
            parentRows.add(Chunk.builder()
                    .tenant(document.getTenant())
                    .document(document)
                    .parentChunkId(null)
                    .content(parent.content())
                    .pageNumber(parent.pageNumber())
                    .chunkIndex(parent.index())
                    .build());
        }
        List<Chunk> savedParents = chunkRepository.saveAll(parentRows);
        chunkRepository.flush();

        // 子块：父块再按句切到 maxChildChars，细粒度提升召回；parent_id 关联真实父块行
        List<ChildBlock> children = buildChildren(savedParents);
        if (children.isEmpty()) {
            throw new IllegalStateException("未生成任何切片");
        }

        List<String> texts = children.stream().map(ChildBlock::content).toList();
        List<float[]> vectors = embeddingService.embedBatch(texts);

        List<Chunk> childRows = new ArrayList<>(children.size());
        for (int i = 0; i < children.size(); i++) {
            ChildBlock child = children.get(i);
            childRows.add(Chunk.builder()
                    .tenant(document.getTenant())
                    .document(document)
                    .parentChunkId(child.parentChunkId())
                    .content(child.content())
                    .pageNumber(child.pageNumber())
                    .chunkIndex(i)
                    .embedding(objectMapper.writeValueAsString(vectors.get(i)))
                    .build());
        }
        List<Chunk> savedChildren = chunkRepository.saveAll(childRows);
        chunkRepository.flush();

        // 维度校验：防止 Embedding 模型与 pgvector vector(dim) 不一致
        if (properties.getPgvector().isEnabled()) {
            int dim = vectors.get(0).length;
            List<ChildBlockView> views = savedChildren.stream()
                    .map(c -> new ChildBlockView(c.getId(), c.getContent(), c.getPageNumber()))
                    .toList();
            PgVectorStoreWriter writer = pgVectorStoreWriter.getIfAvailable();
            if (writer != null) {
                writer.replaceDocument(
                        document.getTenant().getId(),
                        document.getId(),
                        views,
                        vectors,
                        dim);
            }
        }
    }

    private List<ParentBlock> buildParents(List<ParsedSection> sections) {
        List<ParentBlock> parents = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentPage = sections.get(0).pageNumber();
        int index = 0;
        int maxParent = properties.getChunk().getMaxParentChars();

        for (ParsedSection section : sections) {
            if (!current.isEmpty() && current.length() + section.content().length() > maxParent) {
                parents.add(new ParentBlock(index++, current.toString().strip(), currentPage));
                current = new StringBuilder();
            }
            current.append(section.content()).append('\n');
            currentPage = section.pageNumber();
        }
        if (!current.isEmpty()) {
            parents.add(new ParentBlock(index, current.toString().strip(), currentPage));
        }
        return parents;
    }

    private List<ChildBlock> buildChildren(List<Chunk> savedParents) {
        List<ChildBlock> children = new ArrayList<>();
        int maxChild = properties.getChunk().getMaxChildChars();

        for (Chunk parent : savedParents) {
            for (String piece : splitBySentence(parent.getContent(), maxChild)) {
                children.add(new ChildBlock(parent.getId(), piece.strip(), parent.getPageNumber()));
            }
        }
        return children;
    }

    /** 按句边界切分，尽量不超过 maxChars；单句超限时按字符硬切兜底 */
    private List<String> splitBySentence(String text, int maxChars) {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : text.split("(?<=[。！？!?\\.\\n])\\s*")) {
            if (sentence.isBlank()) {
                continue;
            }
            if (sentence.length() > maxChars) {
                if (!current.isEmpty()) {
                    pieces.add(current.toString());
                    current = new StringBuilder();
                }
                for (int i = 0; i < sentence.length(); i += maxChars) {
                    pieces.add(sentence.substring(i, Math.min(sentence.length(), i + maxChars)));
                }
                continue;
            }
            if (current.length() + sentence.length() > maxChars && !current.isEmpty()) {
                pieces.add(current.toString());
                current = new StringBuilder();
            }
            current.append(sentence);
        }
        if (!current.isEmpty()) {
            pieces.add(current.toString());
        }
        return pieces;
    }

    private String abbreviate(String message) {
        if (message == null) {
            return "未知错误";
        }
        return message.length() > 900 ? message.substring(0, 900) : message;
    }

    /** 父块在 parents 列表中的序号，仅用于 chunkIndex */
    private record ParentBlock(int index, String content, int pageNumber) {}

    private record ChildBlock(Long parentChunkId, String content, int pageNumber) {}

    /** pgvector 写入视图（公开给 vector 包） */
    public record ChildBlockView(Long chunkId, String content, int pageNumber) {}
}
