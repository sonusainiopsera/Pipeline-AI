package com.opsera.pipelineassistant.audit;

import com.opsera.pipelineassistant.model.AuditLog;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public final class AuditLogSpecification {

    private AuditLogSpecification() {}

    public static Specification<AuditLog> byAction(String action) {
        if (action == null || action.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("action"), action);
    }

    public static Specification<AuditLog> byResourceType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("resourceType"), resourceType);
    }

    public static Specification<AuditLog> byActorEmailContaining(String actorEmail) {
        if (actorEmail == null || actorEmail.isBlank()) return null;
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("actorEmail")), "%" + actorEmail.toLowerCase() + "%");
    }

    public static Specification<AuditLog> byDateRange(LocalDateTime start, LocalDateTime end) {
        if (start == null && end == null) return null;
        return (root, query, cb) -> {
            if (start != null && end != null) {
                return cb.between(root.get("createdAt"), start, end);
            } else if (start != null) {
                return cb.greaterThanOrEqualTo(root.get("createdAt"), start);
            } else {
                return cb.lessThanOrEqualTo(root.get("createdAt"), end);
            }
        };
    }
}
