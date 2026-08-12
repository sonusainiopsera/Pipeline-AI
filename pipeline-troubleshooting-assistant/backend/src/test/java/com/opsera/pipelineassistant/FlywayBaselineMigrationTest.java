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
        // id, log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at
        assertThat(columnCount).isEqualTo(9);
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

    // ── Flyway history ────────────────────────────────────────────────────────

    @Test
    void flywayHistoryRecordsV1AsApplied() {
        Integer v1Count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '1' AND success = true",
                Integer.class);
        assertThat(v1Count).isEqualTo(1);
    }
}
