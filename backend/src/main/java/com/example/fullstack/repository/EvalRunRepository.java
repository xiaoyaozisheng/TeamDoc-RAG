package com.example.fullstack.repository;

import com.example.fullstack.entity.EvalRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvalRunRepository extends JpaRepository<EvalRun, Long> {

    List<EvalRun> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
