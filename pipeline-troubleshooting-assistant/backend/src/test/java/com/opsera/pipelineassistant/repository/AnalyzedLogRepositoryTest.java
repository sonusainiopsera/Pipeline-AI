package com.opsera.pipelineassistant.repository;

import com.opsera.pipelineassistant.model.AnalyzedLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AnalyzedLogRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AnalyzedLogRepository repository;

    // ── Helpers ────────────────────────────────────────────────────────────────

    private AnalyzedLog buildLog(String category) {
        return AnalyzedLog.builder()
                .logText("Sample pipeline log for category: " + category)
                .category(category)
                .rootCause("Root cause description")
                .suggestedFix("Suggested fix description")
                .customerUpdate("Customer update text")
                .severity("HIGH")
                .confidence(90)
                .build();
    }

    /** Updates createdAt on a persisted entity via JPQL to control ordering. */
    private void setCreatedAt(Long id, LocalDateTime timestamp) {
        entityManager.getEntityManager()
                .createQuery("UPDATE AnalyzedLog a SET a.createdAt = :ts WHERE a.id = :id")
                .setParameter("ts", timestamp)
                .setParameter("id", id)
                .executeUpdate();
    }

    // ── findTop50ByOrderByCreatedAtDesc ────────────────────────────────────────

    @Test
    void shouldReturnLogsOrderedByCreatedAtDescending() {
        LocalDateTime oldest = LocalDateTime.of(2024, 1, 1, 10, 0, 0);
        LocalDateTime middle = LocalDateTime.of(2024, 6, 1, 10, 0, 0);
        LocalDateTime newest = LocalDateTime.of(2025, 1, 1, 10, 0, 0);

        AnalyzedLog log1 = entityManager.persistAndFlush(buildLog("CATEGORY_A"));
        AnalyzedLog log2 = entityManager.persistAndFlush(buildLog("CATEGORY_B"));
        AnalyzedLog log3 = entityManager.persistAndFlush(buildLog("CATEGORY_C"));

        setCreatedAt(log1.getId(), oldest);
        setCreatedAt(log2.getId(), middle);
        setCreatedAt(log3.getId(), newest);
        entityManager.flush();
        entityManager.clear();

        List<AnalyzedLog> result = repository.findTop50ByOrderByCreatedAtDesc();

        assertThat(result).hasSize(3);
        // Newest must come first, oldest last
        assertThat(result.get(0).getId()).isEqualTo(log3.getId());
        assertThat(result.get(1).getId()).isEqualTo(log2.getId());
        assertThat(result.get(2).getId()).isEqualTo(log1.getId());
        assertThat(result.get(0).getCreatedAt()).isEqualTo(newest);
        assertThat(result.get(2).getCreatedAt()).isEqualTo(oldest);
    }

    @Test
    void shouldReturnAtMost50LogsWhenMoreExist() {
        for (int i = 0; i < 55; i++) {
            entityManager.persistAndFlush(buildLog("CATEGORY_X"));
        }
        entityManager.clear();

        List<AnalyzedLog> result = repository.findTop50ByOrderByCreatedAtDesc();

        assertThat(result).hasSize(50);
    }

    @Test
    void shouldReturnEmptyListWhenNoLogsExist() {
        List<AnalyzedLog> result = repository.findTop50ByOrderByCreatedAtDesc();

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnAllFieldsPopulatedForReturnedLogs() {
        entityManager.persistAndFlush(buildLog("MEMORY"));
        entityManager.clear();

        List<AnalyzedLog> result = repository.findTop50ByOrderByCreatedAtDesc();

        assertThat(result).hasSize(1);
        AnalyzedLog log = result.get(0);
        assertThat(log.getCategory()).isEqualTo("MEMORY");
        assertThat(log.getLogText()).isNotBlank();
        assertThat(log.getRootCause()).isNotBlank();
        assertThat(log.getSuggestedFix()).isNotBlank();
        assertThat(log.getSeverity()).isEqualTo("HIGH");
        assertThat(log.getConfidence()).isEqualTo(90);
        assertThat(log.getCreatedAt()).isNotNull();
    }

    // ── categoryCounts ─────────────────────────────────────────────────────────

    @Test
    void shouldReturnCorrectCategoryCountsForMultipleCategories() {
        // 3 Authentication, 2 Docker, 1 Network
        entityManager.persistAndFlush(buildLog("Authentication"));
        entityManager.persistAndFlush(buildLog("Authentication"));
        entityManager.persistAndFlush(buildLog("Authentication"));
        entityManager.persistAndFlush(buildLog("Docker"));
        entityManager.persistAndFlush(buildLog("Docker"));
        entityManager.persistAndFlush(buildLog("Network"));
        entityManager.clear();

        List<Object[]> result = repository.categoryCounts();

        assertThat(result).hasSize(3);
        Map<String, Long> countsByCategory = result.stream()
                .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> (Long) arr[1]
                ));
        assertThat(countsByCategory)
                .containsEntry("Authentication", 3L)
                .containsEntry("Docker", 2L)
                .containsEntry("Network", 1L);
    }

    @Test
    void shouldReturnEmptyCategoryCountsWhenNoLogsExist() {
        List<Object[]> result = repository.categoryCounts();

        assertThat(result).isEmpty();
    }

    @Test
    void shouldGroupAllEntriesUnderSameCategoryAsOneRow() {
        for (int i = 0; i < 5; i++) {
            entityManager.persistAndFlush(buildLog("BUILD_FAILURE"));
        }
        entityManager.clear();

        List<Object[]> result = repository.categoryCounts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0)[0]).isEqualTo("BUILD_FAILURE");
        assertThat(result.get(0)[1]).isEqualTo(5L);
    }
}
