package com.example.fullstack.repository;

import com.example.fullstack.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByName(String name);
    Set<Permission> findByResource(String resource);
    boolean existsByName(String name);
}