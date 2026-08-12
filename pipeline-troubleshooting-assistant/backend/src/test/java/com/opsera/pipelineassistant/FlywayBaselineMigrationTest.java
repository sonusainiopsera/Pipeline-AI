package com.opsera.pipelineassistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that verifies V1__baseline.sql creates both tables correctly in a clean
 * PostgreSQL 16 container and that Hibernate's ddl-auto: validate passes against that schema.
 *
 * Does NOT use @ActiveProfiles("test") — the test profile loads H2. This test intentionally
 * uses PostgreSQL via Testcontainers so it targets the production database engine.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class FlywayBaselineMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("pipelinedb")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        // validate: Hibernate confirms its entity mappings match the Flyway-created schema
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        // Explicitly enable Flyway in case any parent config disables it
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ── Table existence and column counts ─────────────────────────────────────

    @Test
    void errorKnowledgeBaseTableExistsWithCorrectColumnCount() {
        Integer columnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'error_knowledge_base'",
                Integer.class);
        // id, error_pattern, category, root_cause, solution, severity, created_at
        assertThat(columnCount).isEqualTo(7);
    }

    @Test
    void analyzedLogsTableExistsWithCorrectColumnCount() {
        Integer columnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'analyzed_logs'",
                Integer.class);
        // V1: id, log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at
        // V2: + user_id → 10 columns total
        assertThat(columnCount).isEqualTo(10);
    }

    // ── Column type spot-checks ────────────────────────────────────────────────

    @Test
    void errorKnowledgeBaseIdColumnIsBigint() {
        String dataType = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'error_knowledge_base' "
                        + "AND column_name = 'id'",
                String.class);
        assertThat(dataType).isEqualTo("bigint");
    }

    @Test
    void analyzedLogsConfidenceColumnIsInteger() {
        String dataType = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'analyzed_logs' "
                        + "AND column_name = 'confidence'",
                String.class);
        assertThat(dataType).isEqualTo("integer");
    }

    // ── Seed data verification ────────────────────────────────────────────────

    @Test
    void seedDataPopulatedAfterMigration() {
        // SeedData CommandLineRunner inserts 6 entries on first startup
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM error_knowledge_base", Integer.class);
        assertThat(rowCount).isEqualTo(6);
    }

    // ── V2 table existence and column counts ──────────────────────────────────

    @Test
    void usersTableExistsWithCorrectColumnCount() {
        Integer columnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'users'",
                Integer.class);
        // id, email, password_hash, display_name, role, mfa_secret, mfa_enabled,
        // email_verified, verification_token, verification_token_expiry,
        // failed_login_attempts, locked_until, created_at, updated_at
        assertThat(columnCount).isEqualTo(14);
    }

    @Test
    void refreshTokensTableExistsWithCorrectColumnCount() {
        Integer columnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'refresh_tokens'",
                Integer.class);
        // id, user_id, token_hash, expires_at, created_at
        assertThat(columnCount).isEqualTo(5);
    }

    @Test
    void auditLogsTableExistsWithCorrectColumnCount() {
        Integer columnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'audit_logs'",
                Integer.class);
        // id, actor_id, actor_email, action, resource_type, resource_id, details, ip_address, created_at
        assertThat(columnCount).isEqualTo(9);
    }

    @Test
    void analyzedLogsUserIdColumnIsUuid() {
        String dataType = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'analyzed_logs' "
                        + "AND column_name = 'user_id'",
                String.class);
        assertThat(dataType).isEqualTo("uuid");
    }

    @Test
    void usersTableHasRoleCheckConstraint() {
        Integer constraintCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = 'public' AND table_name = 'users' "
                        + "AND constraint_type = 'CHECK'",
                Integer.class);
        assertThat(constraintCount).isGreaterThanOrEqualTo(1);
    }

    // ── Flyway history ────────────────────────────────────────────────────────

    @Test
    void flywayHistoryRecordsV1AsApplied() {
        Integer v1Count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '1' AND success = true",
                Integer.class);
        assertThat(v1Count).isEqualTo(1);
    }

    @Test
    void flywayHistoryRecordsV2AsApplied() {
        Integer v2Count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '2' AND success = true",
                Integer.class);
        assertThat(v2Count).isEqualTo(1);
    }
}
