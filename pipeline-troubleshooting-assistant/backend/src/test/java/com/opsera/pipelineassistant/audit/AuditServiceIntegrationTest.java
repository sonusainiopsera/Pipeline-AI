package com.opsera.pipelineassistant.audit;

import com.opsera.pipelineassistant.model.AuditLog;
import com.opsera.pipelineassistant.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying AuditService end-to-end persistence to PostgreSQL,
 * including JSONB details roundtrip and actor resolution.
 * Requires Docker to be available on the host for Testcontainers PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class AuditServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("pipelinedb_audit")
                    .withUsername("audituser")
                    .withPassword("auditpass");

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
    private AuditService auditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @AfterEach
    void cleanup() {
        auditLogRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    void logEvent_persistsAuditRecordToDatabase() {
        auditService.logEvent("INTEGRATION_TEST", "ErrorKnowledgeBase", "123", null);

        List<AuditLog> records = auditLogRepository.findByResourceTypeOrderByCreatedAtDesc("ErrorKnowledgeBase");
        assertThat(records).hasSize(1);
        AuditLog record = records.get(0);
        assertThat(record.getAction()).isEqualTo("INTEGRATION_TEST");
        assertThat(record.getResourceType()).isEqualTo("ErrorKnowledgeBase");
        assertThat(record.getResourceId()).isEqualTo("123");
        assertThat(record.getCreatedAt()).isNotNull();
    }

    @Test
    void logEvent_persistsJsonbDetailsAndTheyAreRetrievable() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("errorPattern", "connection refused, port 5432");
        details.put("severity", "CRITICAL");
        details.put("matchCount", 7);

        auditService.logEvent("CREATE", "ErrorKnowledgeBase", "200", details);

        List<AuditLog> records = auditLogRepository.findByResourceTypeOrderByCreatedAtDesc("ErrorKnowledgeBase");
        assertThat(records).hasSize(1);
        Map<String, Object> retrieved = records.get(0).getDetails();
        assertThat(retrieved).isNotNull();
        assertThat(retrieved).containsEntry("errorPattern", "connection refused, port 5432");
        assertThat(retrieved).containsEntry("severity", "CRITICAL");
        assertThat(retrieved).containsKey("matchCount");
    }

    @Test
    void logEvent_setsSystemActorWhenNoSecurityContext() {
        // SecurityContextHolder is cleared by @AfterEach — no authentication set

        auditService.logEvent("SYSTEM_TASK", "AnalyzedLog", null, null);

        List<AuditLog> records = auditLogRepository.findByResourceTypeOrderByCreatedAtDesc("AnalyzedLog");
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getActorEmail()).isEqualTo(AuditService.SYSTEM_ACTOR);
    }

    @Test
    void logEvent_persistsAuthenticatedActorEmail() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("integration@test.com", null,
                        List.of(new SimpleGrantedAuthority("ROLE_KB_ADMIN"))));

        auditService.logEvent("UPDATE", "ErrorKnowledgeBase", "55", null);

        List<AuditLog> records = auditLogRepository.findByResourceTypeOrderByCreatedAtDesc("ErrorKnowledgeBase");
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getActorEmail()).isEqualTo("integration@test.com");
    }

    @Test
    void logCreate_persistsRecordWithCreateAction() {
        auditService.logCreate("ErrorKnowledgeBase", "300", Map.of("category", "Docker"));

        List<AuditLog> records = auditLogRepository.findByResourceTypeOrderByCreatedAtDesc("ErrorKnowledgeBase");
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getAction()).isEqualTo("CREATE");
    }
}
