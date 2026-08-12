package com.opsera.pipelineassistant.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogEntityTest {

    @Test
    void builderConstructsEntityWithAllFields() {
        Map<String, Object> details = Map.of("before", "old", "after", "new");

        AuditLog log = AuditLog.builder()
                .actorEmail("alice@example.com")
                .action("CREATE")
                .resourceType("KNOWLEDGE_BASE")
                .resourceId("42")
                .details(details)
                .ipAddress("192.168.1.1")
                .build();

        assertThat(log.getActorEmail()).isEqualTo("alice@example.com");
        assertThat(log.getAction()).isEqualTo("CREATE");
        assertThat(log.getResourceType()).isEqualTo("KNOWLEDGE_BASE");
        assertThat(log.getResourceId()).isEqualTo("42");
        assertThat(log.getDetails()).containsEntry("before", "old").containsEntry("after", "new");
        assertThat(log.getIpAddress()).isEqualTo("192.168.1.1");
    }

    @Test
    void builderAllowsNullDetailsForSimpleEvents() {
        AuditLog log = AuditLog.builder()
                .actorEmail("alice@example.com")
                .action("LOGIN")
                .resourceType("AUTH")
                .build();

        assertThat(log.getDetails()).isNull();
    }

    @Test
    void builderAllowsNullActorForSystemEvents() {
        AuditLog log = AuditLog.builder()
                .actorEmail("system@internal")
                .action("PURGE")
                .resourceType("AUDIT_LOG")
                .build();

        assertThat(log.getActor()).isNull();
    }

    @Test
    void builderAllowsNullResourceIdForEntitylessEvents() {
        AuditLog log = AuditLog.builder()
                .actorEmail("alice@example.com")
                .action("LOGIN")
                .resourceType("AUTH")
                .build();

        assertThat(log.getResourceId()).isNull();
    }

    @Test
    void entityHasNoPublicSetterMethods() {
        Class<AuditLog> clazz = AuditLog.class;
        long setterCount = java.util.Arrays.stream(clazz.getMethods())
                .filter(m -> m.getName().startsWith("set"))
                .count();
        assertThat(setterCount).isZero();
    }

    @Test
    void detailsMapCanContainNestedObjects() {
        Map<String, Object> details = Map.of(
                "before", Map.of("severity", "MEDIUM"),
                "after", Map.of("severity", "HIGH"),
                "changedBy", "admin@example.com"
        );

        AuditLog log = AuditLog.builder()
                .actorEmail("admin@example.com")
                .action("UPDATE")
                .resourceType("KNOWLEDGE_BASE")
                .details(details)
                .build();

        assertThat(log.getDetails()).hasSize(3);
        assertThat(log.getDetails().get("before")).isInstanceOf(Map.class);
    }
}
