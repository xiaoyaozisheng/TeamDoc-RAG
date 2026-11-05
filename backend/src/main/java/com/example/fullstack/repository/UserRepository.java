package com.example.fullstack.repository;

import com.example.fullstack.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByDisplayName(String displayName);
    boolean existsByEmail(String email);
    boolean existsByDisplayName(String displayName);
}