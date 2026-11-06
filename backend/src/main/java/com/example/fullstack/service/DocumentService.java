package com.example.fullstack.service;

import com.example.fullstack.dto.DocumentResponse;
import com.example.fullstack.entity.Chunk;
import com.example.fullstack.entity.Document;
import com.example.fullstack.entity.Tenant;
import com.example.fullstack.entity.User;
import com.example.fullstack.exception.ResourceNotFoundException;
import com.example.fullstack.rag.ingestion.IngestionService;
import com.example.fullstack.rag.ingestion.LocalStorageService;
import com.example.fullstack.repository.ChunkRepository;
import com.example.fullstack.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * 文档管理（DOC-01 ~ DOC-06）：类型/大小校验、租户隔离、软删除 + 向量同步清理、重试入库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "md", "txt", "docx");
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final LocalStorageService storageService;
    private final IngestionService ingestionService;
    private final AuditService auditService;

    public DocumentResponse upload(Tenant tenant, User uploader, MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("不能上传空文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (filename.length() > 200) {
            throw new IllegalArgumentException("文件名过长（最多 200 字符）");
        }
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("不支持的文件类型，仅支持 PDF/MD/TXT/DOCX");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件超过 20MB 大小限制");
        }

        String storageKey = store(file);
        Document document = documentRepository.save(Document.builder()
                .tenant(tenant)
                .name(filename)
                .type(Document.DocType.valueOf(extension.toUpperCase()))
                .storageKey(storageKey)
                .sizeBytes(file.getSize())
                .status(Document.DocStatus.PENDING)
                .uploadedBy(uploader)
                .build());
        log.info("文档上传成功: tenant={}, doc={}", tenant.getId(), document.getId());
        return toResponse(document);
    }

    public List<DocumentResponse> list(Tenant tenant) {
        return documentRepository.findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(tenant.getId())
                .stream().map(this::toResponse).toList();
    }

    public DocumentResponse get(Tenant tenant, Long documentId) {
        return toResponse(findOwnedDocument(tenant, documentId));
    }

    public InputStream download(Tenant tenant, Long documentId) {
        Document document = findOwnedDocument(tenant, documentId);
        return storageService.read(document.getStorageKey());
    }

    /** 重新入库：同步执行并返回最终状态（手动重试入口） */
    public DocumentResponse reindex(Tenant tenant, Long documentId) {
        Document document = findOwnedDocument(tenant, documentId);
        ingestionService.processDocument(document.getId());
        return toResponse(documentRepository.findById(documentId).orElse(document));
    }

    /** 删除（软删）+ 切块/向量同步清理 + 物理文件清理：删除后不再被检索召回（验收 11.1）。单事务保证一致性 */
    @org.springframework.transaction.annotation.Transactional
    public void delete(Tenant tenant, User operator, Long documentId) {
        Document document = findOwnedDocument(tenant, documentId);
        document.setDeleted(true);
        document.setStatus(Document.DocStatus.FAILED);
        document.setErrorMsg("已删除");
        documentRepository.save(document);
        chunkRepository.deleteByDocumentId(document.getId());
        auditService.log(tenant, operator.getId(), "DOCUMENT_DELETE",
                "documentId=" + documentId + ", name=" + document.getName());
        // 物理文件清理（best-effort，软删后下载路径已不可达，残留仅占磁盘）
        try {
            storageService.delete(document.getStorageKey());
        } catch (Exception e) {
            log.warn("文档物理文件删除失败（可人工清理）: {}", document.getStorageKey(), e);
        }
        log.info("文档已删除: tenant={}, doc={}", tenant.getId(), documentId);
    }

    private Document findOwnedDocument(Tenant tenant, Long documentId) {
        return documentRepository.findByIdAndTenantIdAndDeletedFalse(documentId, tenant.getId())
                .orElseThrow(() -> new ResourceNotFoundException("文档不存在或无权访问"));
    }

    private String store(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return storageService.store(in, file.getOriginalFilename());
        } catch (IOException e) {
            throw new IllegalStateException("读取上传文件失败", e);
        }
    }

    private DocumentResponse toResponse(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .name(document.getName())
                .type(document.getType().name())
                .status(document.getStatus().name())
                .errorMsg(document.getErrorMsg())
                .sizeBytes(document.getSizeBytes())
                .uploadedByEmail(document.getUploadedBy() == null ? null : document.getUploadedBy().getEmail())
                .createdAt(document.getCreatedAt())
                .build();
    }
}
