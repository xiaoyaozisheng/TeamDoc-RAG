package com.example.fullstack.repository;

import com.example.fullstack.entity.RetrievalLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface RetrievalLogRepository extends JpaRepository<RetrievalLog, Long> {

    Page<RetrievalLog> findByTenantId(Long tenantId, Pageable pageable);

    Page<RetrievalLog> findByTenantIdAndCreatedAtBetween(Long tenantId, LocalDateTime start, LocalDateTime end, Pageable pageable);
}
