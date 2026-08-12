package com.opsera.pipelineassistant.repository;

import com.opsera.pipelineassistant.audit.AuditLogSpecification;
import com.opsera.pipelineassistant.fixtures.AuditLogTestFixtures;
import com.opsera.pipelineassistant.model.AuditLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AuditLogRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AuditLogRepository repository;

    private void setCreatedAt(Long id, LocalDateTime timestamp) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE audit_logs SET created_at = ? WHERE id = ?")
                .setParameter(1, timestamp)
                .setParameter(2, id)
                .executeUpdate();
    }

    // ── Persistence and JSONB roundtrip ────────────────────────────────────────

    @Test
    void persistsAuditLogWithJsonbDetailsAndRetrievesCorrectly() {
        Map<String, Object> details = Map.of("before", "MEDIUM", "after", "HIGH");
        AuditLog saved = entityManager.persistAndFlush(
                AuditLog.builder()
                        .actorEmail("admin@example.com")
                        .action("UPDATE")
                        .resourceType("KNOWLEDGE_BASE")
                        .resourceId("5")
                        .details(details)
                        .ipAddress("10.0.0.1")
                        .build()
        );
        entityManager.clear();

        AuditLog retrieved = repository.findById(saved.getId()).orElseThrow();

        assertThat(retrieved.getActorEmail()).isEqualTo("admin@example.com");
        assertThat(retrieved.getAction()).isEqualTo("UPDATE");
        assertThat(retrieved.getResourceType()).isEqualTo("KNOWLEDGE_BASE");
        assertThat(retrieved.getDetails()).containsEntry("before", "MEDIUM");
        assertThat(retrieved.getDetails()).containsEntry("after", "HIGH");
        assertThat(retrieved.getCreatedAt()).isNotNull();
    }

    @Test
    void persistsAuditLogWithNullDetails() {
        AuditLog saved = entityManager.persistAndFlush(
                AuditLog.builder()
                        .actorEmail("alice@example.com")
                        .action("LOGIN")
                        .resourceType("AUTH")
                        .build()
        );
        entityManager.clear();

        AuditLog retrieved = repository.findById(saved.getId()).orElseThrow();

        assertThat(retrieved.getDetails()).isNull();
    }

    // ── findByResourceTypeOrderByCreatedAtDesc ─────────────────────────────────

    @Test
    void findByResourceTypeOrderByCreatedAtDescReturnsCorrectlyOrdered() {
        LocalDateTime older = LocalDateTime.of(2024, 1, 1, 10, 0, 0);
        LocalDateTime newer = LocalDateTime.of(2025, 1, 1, 10, 0, 0);

        AuditLog log1 = entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("a@x.com").action("CREATE").resourceType("KNOWLEDGE_BASE").build());
        AuditLog log2 = entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("b@x.com").action("DELETE").resourceType("KNOWLEDGE_BASE").build());
        entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("c@x.com").action("LOGIN").resourceType("AUTH").build());

        setCreatedAt(log1.getId(), older);
        setCreatedAt(log2.getId(), newer);
        entityManager.flush();
        entityManager.clear();

        List<AuditLog> result = repository.findByResourceTypeOrderByCreatedAtDesc("KNOWLEDGE_BASE");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(log2.getId());
        assertThat(result.get(1).getId()).isEqualTo(log1.getId());
    }

    @Test
    void findByResourceTypeOrderByCreatedAtDescReturnsEmptyForUnknownType() {
        entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("a@x.com").action("LOGIN").resourceType("AUTH").build());
        entityManager.clear();

        List<AuditLog> result = repository.findByResourceTypeOrderByCreatedAtDesc("NONEXISTENT");

        assertThat(result).isEmpty();
    }

    // ── findByCreatedAtBefore ──────────────────────────────────────────────────

    @Test
    void findByCreatedAtBeforeReturnsOnlyExpiredRecords() {
        LocalDateTime old = LocalDateTime.of(2023, 1, 1, 0, 0, 0);
        LocalDateTime recent = LocalDateTime.of(2025, 6, 1, 0, 0, 0);
        LocalDateTime cutoff = LocalDateTime.of(2024, 1, 1, 0, 0, 0);

        AuditLog expired = entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("a@x.com").action("LOGIN").resourceType("AUTH").build());
        AuditLog fresh = entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("b@x.com").action("LOGIN").resourceType("AUTH").build());

        setCreatedAt(expired.getId(), old);
        setCreatedAt(fresh.getId(), recent);
        entityManager.flush();
        entityManager.clear();

        List<AuditLog> result = repository.findByCreatedAtBefore(cutoff);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(expired.getId());
    }

    // ── deleteByCreatedAtBefore ────────────────────────────────────────────────

    @Test
    void deleteByCreatedAtBeforeRemovesOnlyExpiredRecords() {
        LocalDateTime old = LocalDateTime.of(2023, 1, 1, 0, 0, 0);
        LocalDateTime recent = LocalDateTime.of(2025, 6, 1, 0, 0, 0);
        LocalDateTime cutoff = LocalDateTime.of(2024, 1, 1, 0, 0, 0);

        AuditLog expired1 = entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("a@x.com").action("LOGIN").resourceType("AUTH").build());
        AuditLog expired2 = entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("b@x.com").action("LOGOUT").resourceType("AUTH").build());
        AuditLog retained = entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("c@x.com").action("CREATE").resourceType("KNOWLEDGE_BASE").build());

        setCreatedAt(expired1.getId(), old);
        setCreatedAt(expired2.getId(), old);
        setCreatedAt(retained.getId(), recent);
        entityManager.flush();
        entityManager.clear();

        repository.deleteByCreatedAtBefore(cutoff);
        entityManager.clear();

        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findById(retained.getId())).isPresent();
    }

    // ── Bulk fixture persistence ───────────────────────────────────────────────

    @Test
    void sampleFixturesCanAllBePersisted() {
        AuditLogTestFixtures.sampleLogs().forEach(entityManager::persistAndFlush);
        entityManager.clear();

        assertThat(repository.count()).isGreaterThanOrEqualTo(5);
    }

    // ── Specification: byAction ────────────────────────────────────────────────

    @Test
    void specByActionReturnsOnlyMatchingAction() {
        entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("a@x.com").action("CREATE").resourceType("KB").build());
        entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("b@x.com").action("DELETE").resourceType("KB").build());
        entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("c@x.com").action("CREATE").resourceType("AUTH").build());
        entityManager.clear();

        Specification<AuditLog> spec = Specification.where(AuditLogSpecification.byAction("CREATE"));
        Page<AuditLog> result = repository.findAll(spec, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2L);
        assertThat(result.getContent()).allMatch(l -> l.getAction().equals("CREATE"));
    }

    @Test
    void specByActionWithNullReturnsAll() {
        entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("a@x.com").action("CREATE").resourceType("KB").build());
        entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("b@x.com").action("DELETE").resourceType("KB").build());
        entityManager.clear();

        Specification<AuditLog> spec = Specification.where(AuditLogSpecification.byAction(null));
        Page<AuditLog> result = repository.findAll(spec, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2L);
    }

    // ── Specification: byResourceType ─────────────────────────────────────────

    @Test
    void specByResourceTypeReturnsOnlyMatchingType() {
        entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("a@x.com").action("CREATE").resourceType("KNOWLEDGE_BASE").build());
        entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("b@x.com").action("LOGIN").resourceType("AUTH").build());
        entityManager.clear();

        Specification<AuditLog> spec = Specification.where(AuditLogSpecification.byResourceType("AUTH"));
        Page<AuditLog> result = repository.findAll(spec, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getContent().get(0).getResourceType()).isEqualTo("AUTH");
    }

    // ── Specification: byActorEmailContaining ─────────────────────────────────

    @Test
    void specByActorEmailContainingFindsPartialMatch() {
        entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("alice@example.com").action("CREATE").resourceType("KB").build());
        entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("bob@example.com").action("DELETE").resourceType("KB").build());
        entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("charlie@other.org").action("LOGIN").resourceType("AUTH").build());
        entityManager.clear();

        Specification<AuditLog> spec = Specification.where(AuditLogSpecification.byActorEmailContaining("example"));
        Page<AuditLog> result = repository.findAll(spec, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2L);
    }

    @Test
    void specByActorEmailContainingIsCaseInsensitive() {
        entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("Admin@Example.com").action("CREATE").resourceType("KB").build());
        entityManager.clear();

        Specification<AuditLog> spec = Specification.where(AuditLogSpecification.byActorEmailContaining("admin"));
        Page<AuditLog> result = repository.findAll(spec, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1L);
    }

    // ── Specification: byDateRange ─────────────────────────────────────────────

    @Test
    void specByDateRangeFiltersCorrectly() {
        AuditLog early = entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("a@x.com").action("CREATE").resourceType("KB").build());
        AuditLog middle = entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("b@x.com").action("UPDATE").resourceType("KB").build());
        AuditLog late = entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("c@x.com").action("DELETE").resourceType("KB").build());

        LocalDateTime t1 = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime t2 = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 1, 1, 0, 0);

        setCreatedAt(early.getId(), t1);
        setCreatedAt(middle.getId(), t2);
        setCreatedAt(late.getId(), t3);
        entityManager.flush();
        entityManager.clear();

        LocalDateTime rangeStart = LocalDateTime.of(2024, 6, 1, 0, 0);
        LocalDateTime rangeEnd = LocalDateTime.of(2025, 6, 1, 0, 0);
        Specification<AuditLog> spec = Specification.where(AuditLogSpecification.byDateRange(rangeStart, rangeEnd));
        Page<AuditLog> result = repository.findAll(spec, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getContent().get(0).getId()).isEqualTo(middle.getId());
    }

    // ── Combined specifications ────────────────────────────────────────────────

    @Test
    void combinedSpecificationsFilterCorrectly() {
        entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("admin@example.com").action("CREATE").resourceType("KNOWLEDGE_BASE").build());
        entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("admin@example.com").action("DELETE").resourceType("KNOWLEDGE_BASE").build());
        entityManager.persistAndFlush(
                AuditLog.builder().actorEmail("user@example.com").action("CREATE").resourceType("KNOWLEDGE_BASE").build());
        entityManager.clear();

        Specification<AuditLog> spec = Specification
                .where(AuditLogSpecification.byAction("CREATE"))
                .and(AuditLogSpecification.byActorEmailContaining("admin"));
        Page<AuditLog> result = repository.findAll(spec, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getContent().get(0).getActorEmail()).isEqualTo("admin@example.com");
        assertThat(result.getContent().get(0).getAction()).isEqualTo("CREATE");
    }
}
