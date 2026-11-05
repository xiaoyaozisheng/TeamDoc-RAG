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
@Table(name = "retrieval_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class RetrievalLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String query;

    @Column(nullable = false)
    private Integer topK;

    @Column(nullable = false)
    private Integer candidateCount;

    /** 融合后候选 JSON：[{chunkId, documentId, documentName, pageNumber, rrfScore}] */
    @Column(columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "latency_ms", nullable = false)
    private Long latencyMs;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
