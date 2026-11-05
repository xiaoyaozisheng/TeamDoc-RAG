package com.example.fullstack.repository;

import com.example.fullstack.entity.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChunkRepository extends JpaRepository<Chunk, Long> {

    List<Chunk> findByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);

    /** 检索候选：本租户下所有携带向量的子块（文档未删除） */
    @Query("select c from Chunk c join c.document d " +
            "where c.tenant.id = :tenantId and c.embedding is not null and d.deleted = false")
    List<Chunk> findSearchableByTenant(@Param("tenantId") Long tenantId);

    @Query("select c from Chunk c join c.document d " +
            "where c.tenant.id = :tenantId and c.embedding is not null and d.deleted = false " +
            "and lower(c.content) like lower(concat('%', :term, '%'))")
    List<Chunk> searchByTerm(@Param("tenantId") Long tenantId, @Param("term") String term);

    List<Chunk> findByIdIn(List<Long> ids);
}
