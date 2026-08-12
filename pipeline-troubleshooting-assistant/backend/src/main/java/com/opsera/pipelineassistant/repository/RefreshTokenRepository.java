package com.opsera.pipelineassistant.repository;

import com.opsera.pipelineassistant.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Transactional
    @Modifying
    void deleteByTokenHash(String tokenHash);

    // Traverses user.id — resolves to WHERE user_id = :userId via Spring Data JPA property path split
    long countByUserId(UUID userId);

    @Transactional
    @Modifying
    void deleteByUserId(UUID userId);

    long countByExpiresAtBefore(LocalDateTime cutoff);

    @Transactional
    @Modifying
    void deleteAllByExpiresAtBefore(LocalDateTime cutoff);
}
