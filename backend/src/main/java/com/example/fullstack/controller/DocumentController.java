package com.example.fullstack.controller;

import com.example.fullstack.dto.DocumentResponse;
import com.example.fullstack.entity.User;
import com.example.fullstack.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "文档管理", description = "知识库文档上传/列表/删除/重试入库")
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "上传文档（DOC-01），异步入库（ING-05）")
    @PreAuthorize("hasAuthority('document:create') or hasRole('ADMIN')")
    @PostMapping
    public DocumentResponse upload(@AuthenticationPrincipal User user,
                                   @RequestParam("file") MultipartFile file) {
        return documentService.upload(user.getTenant(), user, file);
    }

    @Operation(summary = "本团队文档列表")
    @PreAuthorize("hasAuthority('document:read') or hasRole('ADMIN')")
    @GetMapping
    public List<DocumentResponse> list(@AuthenticationPrincipal User user) {
        return documentService.list(user.getTenant());
    }

    @Operation(summary = "文档详情（含解析状态，DOC-03）")
    @PreAuthorize("hasAuthority('document:read') or hasRole('ADMIN')")
    @GetMapping("/{id}")
    public DocumentResponse get(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return documentService.get(user.getTenant(), id);
    }

    @Operation(summary = "下载文档（DOC-06 权限校验）")
    @PreAuthorize("hasAuthority('document:read') or hasRole('ADMIN')")
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal User user,
                                                        @PathVariable Long id) {
        DocumentResponse meta = documentService.get(user.getTenant(), id);
        String encodedName = URLEncoder.encode(meta.getName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(documentService.download(user.getTenant(), id)));
    }

    @Operation(summary = "删除文档（软删 + 向量清理）")
    @PreAuthorize("hasAuthority('document:delete') or hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        documentService.delete(user.getTenant(), user, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "重新入库（失败重试/重建向量，ING-06）")
    @PreAuthorize("hasAuthority('document:update') or hasRole('ADMIN')")
    @PostMapping("/{id}/reindex")
    public DocumentResponse reindex(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return documentService.reindex(user.getTenant(), id);
    }
}
