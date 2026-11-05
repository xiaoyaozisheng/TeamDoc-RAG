package com.example.fullstack.repository;

import com.example.fullstack.entity.RefreshToken;
import com.example.fullstack.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    Set<RefreshToken> findByUser(User user);
    void deleteByUser(User user);
    long countByUserAndRevokedFalse(User user);
}
