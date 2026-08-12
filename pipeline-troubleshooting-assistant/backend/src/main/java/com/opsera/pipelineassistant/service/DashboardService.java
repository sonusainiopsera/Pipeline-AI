package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import com.opsera.pipelineassistant.repository.ErrorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final ErrorRepository errorRepository;
    private final AnalyzedLogRepository analyzedLogRepository;

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        long totalErrors = errorRepository.count();
        long analyzedLogs = analyzedLogRepository.count();

        List<Object[]> categoryCounts = analyzedLogRepository.categoryCounts();
        Map<String, Long> breakdown = new HashMap<>();
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

        stats.put("totalErrors", totalErrors);
        stats.put("analyzedLogs", analyzedLogs);
        stats.put("mostCommonIssue", mostCommonIssue);
        stats.put("categoryBreakdown", breakdown);

        log.info("Dashboard stats retrieved, analyzedLogs={}, mostCommonIssue={}", analyzedLogs, mostCommonIssue);
        return stats;
    }
}
