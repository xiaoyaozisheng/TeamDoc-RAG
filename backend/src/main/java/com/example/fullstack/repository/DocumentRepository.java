package com.example.fullstack.repository;

import com.example.fullstack.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(Long tenantId);

    List<Document> findByStatusInAndDeletedFalse(List<Document.DocStatus> statuses);

    Optional<Document> findByIdAndDeletedFalse(Long id);

    Optional<Document> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);
}
