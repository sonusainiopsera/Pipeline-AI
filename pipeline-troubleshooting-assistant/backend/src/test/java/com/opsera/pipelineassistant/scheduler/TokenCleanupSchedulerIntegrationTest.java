package com.opsera.pipelineassistant.scheduler;

import com.opsera.pipelineassistant.model.RefreshToken;
import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.repository.RefreshTokenRepository;
import com.opsera.pipelineassistant.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying TokenCleanupScheduler end-to-end against a real
 * PostgreSQL instance spun up via Testcontainers. Requires Docker on the host.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class TokenCleanupSchedulerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("pipelinedb_token_cleanup")
                    .withUsername("tokenuser")
                    .withPassword("tokenpass");

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private TokenCleanupScheduler scheduler;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanup() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void schedulerBean_isRegisteredInApplicationContext() {
        assertThat(scheduler).isNotNull();
    }

    @Test
    void cleanup_deletesExpiredRefreshTokensAndPreservesValidOnes() {
        User user = saveTestUser("cleanup-test@example.com");

        // Expired token: expiresAt in the past
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build());

        // Valid token: expiresAt in the future
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build());

        assertThat(refreshTokenRepository.count()).isEqualTo(2);

        scheduler.cleanupExpiredTokens();

        List<RefreshToken> remaining = refreshTokenRepository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void cleanup_clearsExpiredVerificationTokensFromUnverifiedUsers() {
        // Unverified user with expired verification token
        User unverified = userRepository.save(User.builder()
                .email("unverified@example.com")
                .passwordHash("hash")
                .displayName("Unverified")
                .verificationToken(UUID.randomUUID().toString())
                .verificationTokenExpiry(LocalDateTime.now().minusHours(1))
                .emailVerified(false)
                .build());

        // Verified user — must not be touched
        userRepository.save(User.builder()
                .email("verified@example.com")
                .passwordHash("hash")
                .displayName("Verified")
                .emailVerified(true)
                .build());

        scheduler.cleanupExpiredTokens();

        User afterCleanup = userRepository.findById(unverified.getId()).orElseThrow();
        assertThat(afterCleanup.getVerificationToken()).isNull();
        assertThat(afterCleanup.getVerificationTokenExpiry()).isNull();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private User saveTestUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash("hash")
                .displayName("Test User")
                .emailVerified(true)
                .build());
    }
}
