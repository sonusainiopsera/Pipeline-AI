package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.model.AuditLog;
import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import com.opsera.pipelineassistant.repository.AuditLogRepository;
import com.opsera.pipelineassistant.repository.ErrorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying audit records are produced for the full
 * KB create / update / delete lifecycle, using a real PostgreSQL database
 * provisioned via Testcontainers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class KnowledgeBaseAuditIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("pipelinedb_kbaudit")
                    .withUsername("kbaudituser")
                    .withPassword("kbauditpass");

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
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ErrorRepository errorRepository;

    @AfterEach
    void cleanup() {
        auditLogRepository.deleteAll();
        errorRepository.deleteAll();
    }

    @Test
    void createUpdateDeleteSequenceProducesThreeAuditRecords() {
        ErrorKnowledgeBase entry = ErrorKnowledgeBase.builder()
                .errorPattern("connection refused")
                .category("Network")
                .rootCause("Service unreachable")
                .solution("Check firewall rules")
                .severity("HIGH")
                .build();

        ErrorKnowledgeBase created = knowledgeBaseService.create(entry);

        ErrorKnowledgeBase updatePayload = ErrorKnowledgeBase.builder()
                .errorPattern("connection refused, timeout")
                .category("Network")
                .rootCause("Service unreachable or slow")
                .solution("Check firewall rules and latency")
                .severity("CRITICAL")
                .build();
        knowledgeBaseService.update(created.getId(), updatePayload);

        knowledgeBaseService.delete(created.getId());

        List<AuditLog> records = auditLogRepository.findByResourceTypeOrderByCreatedAtDesc("KNOWLEDGE_BASE");
        assertThat(records).hasSize(3);
    }

    @Test
    void createProducesAuditRecordWithCreateActionAndCorrectDetails() {
        ErrorKnowledgeBase entry = ErrorKnowledgeBase.builder()
                .errorPattern("oom,heap")
                .category("Memory")
                .rootCause("Heap exhausted")
                .solution("Increase Xmx")
                .severity("HIGH")
                .build();

        ErrorKnowledgeBase created = knowledgeBaseService.create(entry);

        List<AuditLog> records = auditLogRepository.findByResourceTypeOrderByCreatedAtDesc("KNOWLEDGE_BASE");
        AuditLog createRecord = records.stream()
                .filter(r -> "CREATE".equals(r.getAction()))
                .findFirst()
                .orElseThrow();

        assertThat(createRecord.getResourceId()).isEqualTo(String.valueOf(created.getId()));
        Map<String, Object> details = createRecord.getDetails();
        assertThat(details).containsEntry("category", "Memory");
        assertThat(details).containsEntry("severity", "HIGH");
        assertThat(details).containsEntry("errorPattern", "oom,heap");
    }

    @Test
    void updateProducesAuditRecordWithBeforeAndAfterState() {
        ErrorKnowledgeBase entry = ErrorKnowledgeBase.builder()
                .errorPattern("old-pattern")
                .category("OldCat")
                .rootCause("r")
                .solution("s")
                .severity("LOW")
                .build();
        ErrorKnowledgeBase created = knowledgeBaseService.create(entry);
        auditLogRepository.deleteAll();

        ErrorKnowledgeBase updatePayload = ErrorKnowledgeBase.builder()
                .errorPattern("new-pattern")
                .category("NewCat")
                .rootCause("r2")
                .solution("s2")
                .severity("HIGH")
                .build();
        knowledgeBaseService.update(created.getId(), updatePayload);

        List<AuditLog> records = auditLogRepository.findByResourceTypeOrderByCreatedAtDesc("KNOWLEDGE_BASE");
        assertThat(records).hasSize(1);
        AuditLog updateRecord = records.get(0);
        assertThat(updateRecord.getAction()).isEqualTo("UPDATE");

        Map<String, Object> details = updateRecord.getDetails();
        assertThat(details).containsKey("before");
        assertThat(details).containsKey("after");

        @SuppressWarnings("unchecked")
        Map<String, Object> before = (Map<String, Object>) details.get("before");
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) details.get("after");

        assertThat(before).containsEntry("category", "OldCat");
        assertThat(after).containsEntry("category", "NewCat");
    }

    @Test
    void deleteProducesAuditRecordWithDeleteActionAndEntityState() {
        ErrorKnowledgeBase entry = ErrorKnowledgeBase.builder()
                .errorPattern("del-pattern")
                .category("DelCat")
                .rootCause("r")
                .solution("s")
                .severity("MEDIUM")
                .build();
        ErrorKnowledgeBase created = knowledgeBaseService.create(entry);
        auditLogRepository.deleteAll();

        knowledgeBaseService.delete(created.getId());

        List<AuditLog> records = auditLogRepository.findByResourceTypeOrderByCreatedAtDesc("KNOWLEDGE_BASE");
        assertThat(records).hasSize(1);
        AuditLog deleteRecord = records.get(0);
        assertThat(deleteRecord.getAction()).isEqualTo("DELETE");
        assertThat(deleteRecord.getResourceId()).isEqualTo(String.valueOf(created.getId()));

        Map<String, Object> details = deleteRecord.getDetails();
        assertThat(details).containsEntry("category", "DelCat");
        assertThat(details).containsEntry("severity", "MEDIUM");
        assertThat(details).containsEntry("errorPattern", "del-pattern");
    }
}
