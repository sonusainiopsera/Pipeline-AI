package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import com.opsera.pipelineassistant.repository.ErrorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ErrorRepository errorRepository;

    @Mock
    private AnalyzedLogRepository analyzedLogRepository;

    @InjectMocks
    private DashboardService dashboardService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Builds a List<Object[]> from alternating (category, count) pairs. */
    private List<Object[]> buildCategoryCounts(Object... pairs) {
        List<Object[]> result = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.add(new Object[]{pairs[i], pairs[i + 1]});
        }
        return result;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void shouldReturnCorrectTotalErrors() {
        when(errorRepository.count()).thenReturn(10L);
        when(analyzedLogRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(List.of());

        Map<String, Object> stats = dashboardService.getStats();

        assertThat(stats.get("totalErrors")).isEqualTo(10L);
    }

    @Test
    void shouldReturnCorrectAnalyzedLogsCount() {
        when(errorRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.count()).thenReturn(25L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(List.of());

        Map<String, Object> stats = dashboardService.getStats();

        assertThat(stats.get("analyzedLogs")).isEqualTo(25L);
    }

    @Test
    void shouldDeriveCorrectMostCommonIssue() {
        when(errorRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.count()).thenReturn(3L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(
                buildCategoryCounts("NETWORK", 5L, "BUILD", 12L, "DEPLOY", 3L)
        );

        Map<String, Object> stats = dashboardService.getStats();

        assertThat(stats.get("mostCommonIssue")).isEqualTo("BUILD");
    }

    @Test
    void shouldBuildCategoryBreakdownMap() {
        when(errorRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.count()).thenReturn(3L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(
                buildCategoryCounts("NETWORK", 5L, "BUILD", 12L, "DEPLOY", 3L)
        );

        Map<String, Object> stats = dashboardService.getStats();

        @SuppressWarnings("unchecked")
        Map<String, Long> breakdown = (Map<String, Long>) stats.get("categoryBreakdown");

        assertThat(breakdown)
                .containsEntry("NETWORK", 5L)
                .containsEntry("BUILD", 12L)
                .containsEntry("DEPLOY", 3L)
                .hasSize(3);
    }

    @Test
    void shouldHandleEmptyCategoryCounts() {
        when(errorRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(List.of());

        Map<String, Object> stats = dashboardService.getStats();

        assertThat(stats.get("mostCommonIssue")).isEqualTo("None");

        @SuppressWarnings("unchecked")
        Map<String, Long> breakdown = (Map<String, Long>) stats.get("categoryBreakdown");
        assertThat(breakdown).isEmpty();
    }

    @Test
    void shouldHandleSingleCategory() {
        when(errorRepository.count()).thenReturn(1L);
        when(analyzedLogRepository.count()).thenReturn(1L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(
                buildCategoryCounts("TIMEOUT", 7L)
        );

        Map<String, Object> stats = dashboardService.getStats();

        assertThat(stats.get("mostCommonIssue")).isEqualTo("TIMEOUT");

        @SuppressWarnings("unchecked")
        Map<String, Long> breakdown = (Map<String, Long>) stats.get("categoryBreakdown");
        assertThat(breakdown).containsEntry("TIMEOUT", 7L).hasSize(1);
    }

    @Test
    void shouldReturnZeroCountsWhenRepositoriesAreEmpty() {
        when(errorRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(List.of());

        Map<String, Object> stats = dashboardService.getStats();

        assertThat(stats.get("totalErrors")).isEqualTo(0L);
        assertThat(stats.get("analyzedLogs")).isEqualTo(0L);
    }

    @Test
    void shouldSelectFirstCategoryWhenCountsAreTied() {
        // When two categories share the highest count, the first iterated is kept
        // (the comparison is strictly greater-than).
        when(errorRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.count()).thenReturn(2L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(
                buildCategoryCounts("ALPHA", 10L, "BETA", 10L)
        );

        Map<String, Object> stats = dashboardService.getStats();

        // ALPHA is processed first; BETA's count is not strictly greater, so ALPHA wins.
        assertThat(stats.get("mostCommonIssue")).isEqualTo("ALPHA");
    }

    @Test
    void shouldHandleLargeCountValues() {
        when(errorRepository.count()).thenReturn(Long.MAX_VALUE);
        when(analyzedLogRepository.count()).thenReturn(Long.MAX_VALUE);
        when(analyzedLogRepository.categoryCounts()).thenReturn(
                buildCategoryCounts("CRITICAL", Long.MAX_VALUE)
        );

        Map<String, Object> stats = dashboardService.getStats();

        assertThat(stats.get("totalErrors")).isEqualTo(Long.MAX_VALUE);
        assertThat(stats.get("analyzedLogs")).isEqualTo(Long.MAX_VALUE);
        assertThat(stats.get("mostCommonIssue")).isEqualTo("CRITICAL");
    }
}
