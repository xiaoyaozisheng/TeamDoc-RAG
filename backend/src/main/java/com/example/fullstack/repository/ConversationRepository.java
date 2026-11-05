package com.example.fullstack.repository;

import com.example.fullstack.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByTenantIdOrderByCreatedAtDesc(Long tenantId, org.springframework.data.domain.Pageable pageable);

    Optional<Conversation> findByIdAndTenantId(Long id, Long tenantId);
}
