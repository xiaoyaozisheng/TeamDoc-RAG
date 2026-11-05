package com.example.fullstack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "chunks", indexes = {
        @Index(name = "idx_chunks_tenant", columnList = "tenant_id"),
        @Index(name = "idx_chunks_document", columnList = "document_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Chunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    /** 父块 id；null 表示该行本身是父块 */
    @Column(name = "parent_id")
    private Long parentChunkId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "page_number", nullable = false)
    @Builder.Default
    private Integer pageNumber = 1;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    /** 仅子块有值：Embedding 向量的 JSON 数组；父块只供 LLM 使用，不参与检索 */
    @Column(columnDefinition = "TEXT")
    private String embedding;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
