package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.dto.Responses.CategoryStat;
import com.opsera.pipelineassistant.dto.Responses.DashboardResponse;
import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import com.opsera.pipelineassistant.repository.ErrorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final ErrorRepository errorRepository;
    private final AnalyzedLogRepository analyzedLogRepository;

    public DashboardResponse getStats() {
        long totalErrors = errorRepository.count();
        long analyzedLogs = analyzedLogRepository.count();

        int averageConfidence = analyzedLogRepository.findAverageConfidence()
                .map(d -> (int) Math.round(d))
                .orElse(0);

        LocalDateTime now = LocalDateTime.now();
        long analysesLast7Days = analyzedLogRepository.countByCreatedAtAfter(now.minusDays(7));
        long analysesLast30Days = analyzedLogRepository.countByCreatedAtAfter(now.minusDays(30));

        List<Object[]> categoryCounts = analyzedLogRepository.categoryCounts();
        Map<String, Long> breakdown = new LinkedHashMap<>();
        String mostCommonIssue = "None";
        long maxCount = 0;

        for (Object[] row : categoryCounts) {
            String category = (String) row[0];
            Long count = (Long) row[1];
            breakdown.put(category, count);
            if (count > maxCount) {
                maxCount = count;
                mostCommonIssue = category;
            }
        }

        List<Object[]> topRaw = analyzedLogRepository.findTopCategoriesByCount(PageRequest.of(0, 5));
        List<CategoryStat> topCategories = topRaw.stream()
                .map(row -> {
                    String cat = (String) row[0];
                    long cnt = (Long) row[1];
                    double pct = analyzedLogs > 0 ? Math.round(cnt * 1000.0 / analyzedLogs) / 10.0 : 0.0;
                    return new CategoryStat(cat, cnt, pct);
                })
                .collect(Collectors.toList());

        log.info("Dashboard stats retrieved, analyzedLogs={}, mostCommonIssue={}", analyzedLogs, mostCommonIssue);

        return new DashboardResponse(
                totalErrors, analyzedLogs, mostCommonIssue, breakdown,
                averageConfidence, analysesLast7Days, analysesLast30Days, topCategories
        );
    }
}
