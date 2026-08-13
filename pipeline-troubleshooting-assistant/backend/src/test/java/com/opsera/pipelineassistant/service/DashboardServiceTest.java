package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.dto.Responses.CategoryStat;
import com.opsera.pipelineassistant.dto.Responses.DashboardResponse;
import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import com.opsera.pipelineassistant.repository.ErrorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ErrorRepository errorRepository;

    @Mock
    private AnalyzedLogRepository analyzedLogRepository;

    @InjectMocks
    private DashboardService dashboardService;

    // ── Default stubs for new aggregate methods ───────────────────────────────

    @BeforeEach
    void setUp() {
        lenient().when(analyzedLogRepository.findAverageConfidence()).thenReturn(Optional.empty());
        lenient().when(analyzedLogRepository.countByCreatedAtAfter(any())).thenReturn(0L);
        lenient().when(analyzedLogRepository.findTopCategoriesByCount(any(Pageable.class))).thenReturn(List.of());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Builds a List<Object[]> from alternating (category, count) pairs. */
    private List<Object[]> buildCategoryCounts(Object... pairs) {
        List<Object[]> result = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.add(new Object[]{pairs[i], pairs[i + 1]});
        }
        return result;
    }

    // ── Existing tests (updated to use DashboardResponse) ─────────────────────

    @Test
    void shouldReturnCorrectTotalErrors() {
        when(errorRepository.count()).thenReturn(10L);
        when(analyzedLogRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(List.of());

        DashboardResponse stats = dashboardService.getStats();

        assertThat(stats.totalErrors()).isEqualTo(10L);
    }

    @Test
    void shouldReturnCorrectAnalyzedLogsCount() {
        when(errorRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.count()).thenReturn(25L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(List.of());

        DashboardResponse stats = dashboardService.getStats();

        assertThat(stats.analyzedLogs()).isEqualTo(25L);
    }

    @Test
    void shouldDeriveCorrectMostCommonIssue() {
        when(errorRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.count()).thenReturn(3L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(
                buildCategoryCounts("NETWORK", 5L, "BUILD", 12L, "DEPLOY", 3L)
        );

        DashboardResponse stats = dashboardService.getStats();

        assertThat(stats.mostCommonIssue()).isEqualTo("BUILD");
    }

    @Test
    void shouldBuildCategoryBreakdownMap() {
        when(errorRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.count()).thenReturn(3L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(
                buildCategoryCounts("NETWORK", 5L, "BUILD", 12L, "DEPLOY", 3L)
        );

        DashboardResponse stats = dashboardService.getStats();

        assertThat(stats.categoryBreakdown())
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

        DashboardResponse stats = dashboardService.getStats();

        assertThat(stats.mostCommonIssue()).isEqualTo("None");
        assertThat(stats.categoryBreakdown()).isEmpty();
    }

    @Test
    void shouldHandleSingleCategory() {
        when(errorRepository.count()).thenReturn(1L);
        when(analyzedLogRepository.count()).thenReturn(1L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(
                buildCategoryCounts("TIMEOUT", 7L)
        );

        DashboardResponse stats = dashboardService.getStats();

        assertThat(stats.mostCommonIssue()).isEqualTo("TIMEOUT");
        assertThat(stats.categoryBreakdown()).containsEntry("TIMEOUT", 7L).hasSize(1);
    }

    @Test
    void shouldReturnZeroCountsWhenRepositoriesAreEmpty() {
        when(errorRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(List.of());

        DashboardResponse stats = dashboardService.getStats();

        assertThat(stats.totalErrors()).isEqualTo(0L);
        assertThat(stats.analyzedLogs()).isEqualTo(0L);
    }

    @Test
    void shouldSelectFirstCategoryWhenCountsAreTied() {
        when(errorRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.count()).thenReturn(2L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(
                buildCategoryCounts("ALPHA", 10L, "BETA", 10L)
        );

        DashboardResponse stats = dashboardService.getStats();

        assertThat(stats.mostCommonIssue()).isEqualTo("ALPHA");
    }

    @Test
    void shouldHandleLargeCountValues() {
        when(errorRepository.count()).thenReturn(Long.MAX_VALUE);
        when(analyzedLogRepository.count()).thenReturn(Long.MAX_VALUE);
        when(analyzedLogRepository.categoryCounts()).thenReturn(
                buildCategoryCounts("CRITICAL", Long.MAX_VALUE)
        );

        DashboardResponse stats = dashboardService.getStats();

        assertThat(stats.totalErrors()).isEqualTo(Long.MAX_VALUE);
        assertThat(stats.analyzedLogs()).isEqualTo(Long.MAX_VALUE);
        assertThat(stats.mostCommonIssue()).isEqualTo("CRITICAL");
    }

    // ── New tests for averageConfidence ────────────────────────────────────────

    @Test
    void shouldReturnAverageConfidenceRoundedToNearestInteger() {
        when(errorRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.count()).thenReturn(2L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(List.of());
        when(analyzedLogRepository.findAverageConfidence()).thenReturn(Optional.of(72.6));

        DashboardResponse stats = dashboardService.getStats();

        assertThat(stats.averageConfidence()).isEqualTo(73);
    }

    @Test
    void shouldReturnZeroAverageConfidenceWhenTableIsEmpty() {
        when(errorRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(List.of());
        when(analyzedLogRepository.findAverageConfidence()).thenReturn(Optional.empty());

        DashboardResponse stats = dashboardService.getStats();

        assertThat(stats.averageConfidence()).isEqualTo(0);
    }

    @Test
    void shouldRoundAverageConfidenceDown() {
        when(errorRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.count()).thenReturn(1L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(List.of());
        when(analyzedLogRepository.findAverageConfidence()).thenReturn(Optional.of(72.4));

        DashboardResponse stats = dashboardService.getStats();

        assertThat(stats.averageConfidence()).isEqualTo(72);
    }

    // ── New tests for time-range counts ───────────────────────────────────────

    @Test
    void shouldReturnAnalysesLast7DaysFromRepository() {
        when(errorRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.count()).thenReturn(50L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(List.of());
        when(analyzedLogRepository.countByCreatedAtAfter(any())).thenAnswer(inv -> {
            java.time.LocalDateTime since = inv.getArgument(0);
            java.time.LocalDateTime sevenDaysAgo = java.time.LocalDateTime.now().minusDays(8);
            return since.isAfter(sevenDaysAgo) ? 12L : 35L;
        });

        DashboardResponse stats = dashboardService.getStats();

        assertThat(stats.analysesLast7Days()).isEqualTo(12L);
        assertThat(stats.analysesLast30Days()).isEqualTo(35L);
        assertThat(stats.analysesLast30Days()).isGreaterThanOrEqualTo(stats.analysesLast7Days());
    }

    @Test
    void shouldReturnZeroTimeRangeCountsWhenEmpty() {
        when(errorRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(List.of());

        DashboardResponse stats = dashboardService.getStats();

        assertThat(stats.analysesLast7Days()).isEqualTo(0L);
        assertThat(stats.analysesLast30Days()).isEqualTo(0L);
    }

    // ── New tests for topCategories ───────────────────────────────────────────

    @Test
    void shouldReturnTopCategoriesWithCorrectPercentage() {
        when(errorRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.count()).thenReturn(100L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(List.of());
        when(analyzedLogRepository.findTopCategoriesByCount(any(Pageable.class))).thenReturn(
                buildCategoryCounts("Memory", 40L, "Network", 30L, "Docker", 20L)
        );

        DashboardResponse stats = dashboardService.getStats();

        assertThat(stats.topCategories()).hasSize(3);
        CategoryStat top = stats.topCategories().get(0);
        assertThat(top.category()).isEqualTo("Memory");
        assertThat(top.count()).isEqualTo(40L);
        assertThat(top.percentage()).isEqualTo(40.0);

        CategoryStat second = stats.topCategories().get(1);
        assertThat(second.category()).isEqualTo("Network");
        assertThat(second.percentage()).isEqualTo(30.0);
    }

    @Test
    void shouldReturnEmptyTopCategoriesWhenNoLogs() {
        when(errorRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(List.of());

        DashboardResponse stats = dashboardService.getStats();

        assertThat(stats.topCategories()).isEmpty();
    }

    @Test
    void shouldReturnSingleTopCategoryWith100PercentWhenOneCategory() {
        when(errorRepository.count()).thenReturn(0L);
        when(analyzedLogRepository.count()).thenReturn(5L);
        when(analyzedLogRepository.categoryCounts()).thenReturn(
                buildCategoryCounts("BUILD", 5L)
        );
        when(analyzedLogRepository.findTopCategoriesByCount(any(Pageable.class))).thenReturn(
                buildCategoryCounts("BUILD", 5L)
        );

        DashboardResponse stats = dashboardService.getStats();

        assertThat(stats.topCategories()).hasSize(1);
        assertThat(stats.topCategories().get(0).percentage()).isEqualTo(100.0);
    }
}
