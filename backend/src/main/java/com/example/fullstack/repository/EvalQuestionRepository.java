package com.example.fullstack.repository;

import com.example.fullstack.entity.EvalQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvalQuestionRepository extends JpaRepository<EvalQuestion, Long> {

    List<EvalQuestion> findByTenantId(Long tenantId);
}
