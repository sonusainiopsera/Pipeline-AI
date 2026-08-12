package com.opsera.pipelineassistant.scheduler;

import com.opsera.pipelineassistant.model.AnalyzedLog;
import com.opsera.pipelineassistant.model.AuditLog;
import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import com.opsera.pipelineassistant.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying DataRetentionScheduler end-to-end against a real
 * PostgreSQL instance. JdbcTemplate is used to backdate created_at timestamps
 * after JPA inserts so the scheduler sees them as eligible for purge.
 * Requires Docker to be available on the host for Testcontainers PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class DataRetentionSchedulerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("pipelinedb_retention")
                    .withUsername("retentionuser")
                    .withPassword("retentionpass");

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("retention.analyzed-logs-days", () -> "90");
        registry.add("retention.audit-logs-days", () -> "365");
    }

    @Autowired
    private DataRetentionScheduler scheduler;

    @Autowired
    private AnalyzedLogRepository analyzedLogRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        auditLogRepository.deleteAll();
        analyzedLogRepository.deleteAll();
    }

    @Test
    void purge_deletesOldRecordsAndPreservesRecentOnes() {
        // Insert 3 old records (100 days ago) — eligible for purge
        for (int i = 0; i < 3; i++) {
            AnalyzedLog saved = analyzedLogRepository.save(
                    AnalyzedLog.builder().logText("old log " + i).category("Docker").build());
            backdateAnalyzedLog(saved.getId(), 100);
        }
        // Insert 2 recent records (10 days ago) — must survive
        for (int i = 0; i < 2; i++) {
            analyzedLogRepository.save(
                    AnalyzedLog.builder().logText("recent log " + i).category("Auth").build());
        }

        assertThat(analyzedLogRepository.count()).isEqualTo(5);

        scheduler.purgeExpiredRecords();

        List<AnalyzedLog> remaining = analyzedLogRepository.findAll();
        assertThat(remaining).hasSize(2);
        assertThat(remaining).allMatch(l -> l.getLogText().startsWith("recent log"));
    }

    @Test
    void purge_createsAuditEventWithCorrectDeleteCount() {
        // Insert 3 old records eligible for purge
        for (int i = 0; i < 3; i++) {
            AnalyzedLog saved = analyzedLogRepository.save(
                    AnalyzedLog.builder().logText("audit-count log " + i).category("Memory").build());
            backdateAnalyzedLog(saved.getId(), 100);
        }

        scheduler.purgeExpiredRecords();

        List<AuditLog> purgeEvents = auditLogRepository.findByResourceTypeOrderByCreatedAtDesc("ANALYSIS");
        assertThat(purgeEvents).hasSize(1);
        AuditLog event = purgeEvents.get(0);
        assertThat(event.getAction()).isEqualTo("PURGE");
        assertThat(event.getActorEmail()).isEqualTo("SYSTEM");
        assertThat(event.getDetails()).containsKey("analyzedLogsDeleted");
        assertThat(event.getDetails().get("analyzedLogsDeleted")).isEqualTo(3);
    }

    @Test
    void purge_createsNoAuditEventWhenNoRecordsAreEligible() {
        // Insert only a recent record (10 days old — within 90-day retention)
        analyzedLogRepository.save(
                AnalyzedLog.builder().logText("very recent log").category("Auth").build());

        scheduler.purgeExpiredRecords();

        List<AuditLog> purgeEvents = auditLogRepository.findByResourceTypeOrderByCreatedAtDesc("ANALYSIS");
        assertThat(purgeEvents).isEmpty();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Backdates the created_at of an analyzed_log record using JdbcTemplate.
     * JPA's @CreationTimestamp sets created_at at INSERT time; this bypasses that
     * to simulate records inserted in the past, making them eligible for purge.
     */
    private void backdateAnalyzedLog(Long id, int daysAgo) {
        Timestamp ts = Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minusDays(daysAgo));
        jdbcTemplate.update("UPDATE analyzed_logs SET created_at = ? WHERE id = ?", ts, id);
    }
}
