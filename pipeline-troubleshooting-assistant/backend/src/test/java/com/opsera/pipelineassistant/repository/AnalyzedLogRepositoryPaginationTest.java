package com.opsera.pipelineassistant.repository;

import com.opsera.pipelineassistant.model.AnalyzedLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AnalyzedLogRepositoryPaginationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AnalyzedLogRepository repository;

    private static final int TOTAL_RECORDS = 25;

    @BeforeEach
    void seed() {
        // Insert 25 AnalyzedLog records with distinct created_at timestamps (1 hour apart)
        // so descending order is deterministic. Record 25 is the newest.
        LocalDateTime base = LocalDateTime.of(2024, 1, 1, 1, 0, 0);
        for (int i = 1; i <= TOTAL_RECORDS; i++) {
            AnalyzedLog log = AnalyzedLog.builder()
                    .logText("Log text " + String.format("%02d", i))
                    .category(categoryFor(i))
                    .rootCause("Root cause " + i)
                    .suggestedFix("Suggested fix " + i)
                    .customerUpdate("Customer update " + i)
                    .severity(i % 3 == 0 ? "MEDIUM" : "HIGH")
                    .confidence(50 + i)
                    .build();
            AnalyzedLog persisted = entityManager.persistAndFlush(log);
            // Set distinct created_at via JPQL to control ordering
            entityManager.getEntityManager()
                    .createQuery("UPDATE AnalyzedLog a SET a.createdAt = :ts WHERE a.id = :id")
                    .setParameter("ts", base.plusHours(i))
                    .setParameter("id", persisted.getId())
                    .executeUpdate();
        }
        entityManager.flush();
        entityManager.clear();
    }

    // ── Page size and total counts ─────────────────────────────────────────────

    @Test
    void pageSizeOf10ReturnsTenItemsFromTwentyFiveTotal() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<AnalyzedLog> page = repository.findAllByOrderByCreatedAtDesc(pageable);

        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getTotalElements()).isEqualTo(25);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getNumber()).isEqualTo(0);
    }

    // ── Descending ordering ────────────────────────────────────────────────────

    @Test
    void firstPageContainsNewestRecords() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<AnalyzedLog> page = repository.findAllByOrderByCreatedAtDesc(pageable);

        List<AnalyzedLog> items = page.getContent();
        // Records are inserted with base + 1h to base + 25h; newest is record 25 (base + 25h)
        assertThat(items.get(0).getCreatedAt())
                .isAfter(items.get(items.size() - 1).getCreatedAt());
    }

    @Test
    void resultsAreOrderedByCreatedAtDescendingAcrossFullPage() {
        Pageable pageable = PageRequest.of(0, 25);
        Page<AnalyzedLog> page = repository.findAllByOrderByCreatedAtDesc(pageable);

        List<LocalDateTime> timestamps = page.getContent().stream()
                .map(AnalyzedLog::getCreatedAt)
                .toList();

        for (int i = 0; i < timestamps.size() - 1; i++) {
            assertThat(timestamps.get(i))
                    .as("Item %d should be newer than item %d", i, i + 1)
                    .isAfterOrEqualTo(timestamps.get(i + 1));
        }
    }

    // ── Page 2 (middle page) ───────────────────────────────────────────────────

    @Test
    void secondPageReturnsTenItems() {
        Pageable pageable = PageRequest.of(1, 10);
        Page<AnalyzedLog> page = repository.findAllByOrderByCreatedAtDesc(pageable);

        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getTotalElements()).isEqualTo(25);
    }

    // ── Last page (page 2, index 2) ────────────────────────────────────────────

    @Test
    void thirdPageReturnsFiveRemainingItems() {
        Pageable pageable = PageRequest.of(2, 10);
        Page<AnalyzedLog> page = repository.findAllByOrderByCreatedAtDesc(pageable);

        assertThat(page.getContent()).hasSize(5);
        assertThat(page.getNumber()).isEqualTo(2);
        assertThat(page.isLast()).isTrue();
    }

    // ── Beyond last page ───────────────────────────────────────────────────────

    @Test
    void pageIndexBeyondLastReturnsEmptyContentWithCorrectMetadata() {
        Pageable pageable = PageRequest.of(99, 10);
        Page<AnalyzedLog> page = repository.findAllByOrderByCreatedAtDesc(pageable);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(25);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }

    // ── Empty table ────────────────────────────────────────────────────────────

    @Test
    void emptyTableReturnsPageWithZeroElementsAndZeroPages() {
        // Remove all seeded records
        repository.deleteAll();
        entityManager.flush();
        entityManager.clear();

        Pageable pageable = PageRequest.of(0, 10);
        Page<AnalyzedLog> page = repository.findAllByOrderByCreatedAtDesc(pageable);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(0);
        assertThat(page.getTotalPages()).isEqualTo(0);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static String categoryFor(int i) {
        return switch (i % 5) {
            case 0 -> "Memory";
            case 1 -> "Network";
            case 2 -> "Docker";
            case 3 -> "Permissions";
            default -> "Dependency";
        };
    }
}
