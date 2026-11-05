package com.example.fullstack.rag.ingestion;

import com.example.fullstack.config.RagProperties;
import com.example.fullstack.entity.Document;
import com.example.fullstack.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 入库 Worker（ING-05）：定时扫描 PENDING / 可重试的 FAILED 文档。
 * MVP 用数据库任务状态 + 定时扫描，避免过早引入消息队列（PRD 技术约束）。
 * 测试与手动 reindex 直接调用 IngestionService.processDocument，不依赖调度。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.ingestion.worker-enabled", havingValue = "true", matchIfMissing = true)
public class IngestionWorker {

    private final DocumentRepository documentRepository;
    private final IngestionService ingestionService;
    private final RagProperties properties;

    @Scheduled(fixedDelayString = "${rag.ingestion.scan-interval-ms:3000}")
    public void scan() {
        List<Document> pending = documentRepository.findByStatusInAndDeletedFalse(
                List.of(Document.DocStatus.PENDING, Document.DocStatus.FAILED));
        int maxRetries = properties.getIngestion().getMaxRetries();
        for (Document document : pending) {
            if (document.getStatus() == Document.DocStatus.FAILED
                    && document.getRetryCount() != null
                    && document.getRetryCount() >= maxRetries) {
                continue; // 超过重试上限，等待人工 reindex
            }
            try {
                ingestionService.processDocument(document.getId());
            } catch (Exception e) {
                log.error("Worker 处理文档失败: {}", document.getId(), e);
            }
        }
    }
}
