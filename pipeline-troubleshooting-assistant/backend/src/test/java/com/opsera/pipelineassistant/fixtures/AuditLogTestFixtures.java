package com.opsera.pipelineassistant.fixtures;

import com.opsera.pipelineassistant.model.AuditLog;

import java.util.List;
import java.util.Map;

/**
 * Factory for AuditLog test data. Produces immutable sample records
 * with varied actions, resource types, and JSONB details for use in
 * unit and integration tests.
 */
public final class AuditLogTestFixtures {

    private AuditLogTestFixtures() {}

    public static AuditLog loginEvent() {
        return AuditLog.builder()
                .actorEmail("alice@example.com")
                .action("LOGIN")
                .resourceType("AUTH")
                .ipAddress("10.0.0.1")
                .build();
    }

    public static AuditLog loginFailedEvent() {
        return AuditLog.builder()
                .actorEmail("bob@example.com")
                .action("LOGIN_FAILED")
                .resourceType("AUTH")
                .details(Map.of("reason", "invalid_password", "attempts", 3))
                .ipAddress("10.0.0.2")
                .build();
    }

    public static AuditLog createKnowledgeBaseEntry() {
        return AuditLog.builder()
                .actorEmail("admin@example.com")
                .action("CREATE")
                .resourceType("KNOWLEDGE_BASE")
                .resourceId("42")
                .details(Map.of(
                        "after", Map.of(
                                "errorPattern", "OutOfMemoryError",
                                "category", "Memory",
                                "severity", "HIGH"
                        )
                ))
                .ipAddress("10.0.0.3")
                .build();
    }

    public static AuditLog updateKnowledgeBaseEntry() {
        return AuditLog.builder()
                .actorEmail("admin@example.com")
                .action("UPDATE")
                .resourceType("KNOWLEDGE_BASE")
                .resourceId("42")
                .details(Map.of(
                        "before", Map.of("severity", "MEDIUM"),
                        "after", Map.of("severity", "HIGH")
                ))
                .ipAddress("10.0.0.3")
                .build();
    }

    public static AuditLog deleteKnowledgeBaseEntry() {
        return AuditLog.builder()
                .actorEmail("admin@example.com")
                .action("DELETE")
                .resourceType("KNOWLEDGE_BASE")
                .resourceId("99")
                .details(Map.of("before", Map.of("errorPattern", "legacy pattern")))
                .ipAddress("10.0.0.3")
                .build();
    }

    public static AuditLog analyzeEvent() {
        return AuditLog.builder()
                .actorEmail("alice@example.com")
                .action("ANALYZE")
                .resourceType("ANALYZED_LOG")
                .resourceId("7")
                .details(Map.of("category", "Memory", "confidence", 87))
                .ipAddress("10.0.0.1")
                .build();
    }

    public static AuditLog systemPurgeEvent() {
        return AuditLog.builder()
                .actorEmail("system@internal")
                .action("PURGE")
                .resourceType("AUDIT_LOG")
                .details(Map.of("deletedCount", 150, "cutoffDays", 90))
                .build();
    }

    /**
     * Returns 5 sample records covering LOGIN, LOGIN_FAILED, CREATE, UPDATE, and ANALYZE
     * actions with varied resource types and JSONB details.
     */
    public static List<AuditLog> sampleLogs() {
        return List.of(
                loginEvent(),
                loginFailedEvent(),
                createKnowledgeBaseEntry(),
                updateKnowledgeBaseEntry(),
                analyzeEvent()
        );
    }
}
