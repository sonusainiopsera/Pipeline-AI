package com.opsera.pipelineassistant.repository;

import com.opsera.pipelineassistant.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByVerificationToken(String verificationToken);

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.verificationToken = null, u.verificationTokenExpiry = null " +
           "WHERE u.emailVerified = false AND u.verificationTokenExpiry < :cutoff")
    int clearExpiredVerificationTokens(@Param("cutoff") LocalDateTime cutoff);
}
