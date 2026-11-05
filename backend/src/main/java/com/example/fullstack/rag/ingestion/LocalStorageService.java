package com.example.fullstack.rag.ingestion;

import com.example.fullstack.config.RagProperties;
import com.example.fullstack.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 本地卷文件存储（DOC-04 的 MVP 实现）。
 * 文件不入数据库大字段；生产可替换为 MinIO/S3，接口不变。
 */
@Service
@RequiredArgsConstructor
public class LocalStorageService {

    private final RagProperties properties;

    public String store(InputStream content, String originalFilename) {
        String extension = extensionOf(originalFilename);
        String storageKey = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        try {
            Path base = Path.of(properties.getStorage().getBaseDir());
            Files.createDirectories(base);
            Files.copy(content, base.resolve(storageKey), StandardCopyOption.REPLACE_EXISTING);
            return storageKey;
        } catch (IOException e) {
            throw new IllegalStateException("文件存储失败: " + e.getMessage(), e);
        }
    }

    public InputStream read(String storageKey) {
        try {
            return Files.newInputStream(resolve(storageKey));
        } catch (IOException e) {
            throw new ResourceNotFoundException("文件不存在或已被清理");
        }
    }

    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            // 物理清理失败不阻塞业务删除，后续可由清理任务兜底
        }
    }

    private Path resolve(String storageKey) {
        Path base = Path.of(properties.getStorage().getBaseDir()).toAbsolutePath().normalize();
        Path target = base.resolve(storageKey).normalize();
        // 防路径穿越：storageKey 必须仍位于存储根目录内
        if (!target.startsWith(base)) {
            throw new ResourceNotFoundException("非法的文件路径");
        }
        return target;
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }
}
