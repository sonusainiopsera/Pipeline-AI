package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.model.AnalyzedLog;
import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import com.opsera.pipelineassistant.repository.ErrorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final ErrorRepository errorRepository;
    private final AnalyzedLogRepository analyzedLogRepository;

    public AnalyzedLog analyze(String logText) {
        List<ErrorKnowledgeBase> patterns = errorRepository.findAll();

        ErrorKnowledgeBase bestMatch = null;
        int bestMatchCount = 0;

        for (ErrorKnowledgeBase pattern : patterns) {
            String[] keywords = pattern.getErrorPattern().split(",");
            int matchCount = 0;
            for (String keyword : keywords) {
                if (logText.toLowerCase().contains(keyword.trim().toLowerCase())) {
                    matchCount++;
                }
            }
            if (matchCount > bestMatchCount) {
                bestMatchCount = matchCount;
                bestMatch = pattern;
            }
        }

        int confidence;
        String category;
        String rootCause;
        String suggestedFix;
        String severity;
        String customerUpdate;

        if (bestMatch != null && bestMatchCount > 0) {
            int totalKeywords = bestMatch.getErrorPattern().split(",").length;
            confidence = Math.min(98, 55 + (bestMatchCount * 43 / Math.max(totalKeywords, 1)));
            category = bestMatch.getCategory();
            rootCause = bestMatch.getRootCause();
            suggestedFix = bestMatch.getSolution();
            severity = bestMatch.getSeverity();
            customerUpdate = String.format(
                "We have identified the root cause of your pipeline failure as: %s. "
                    + "We recommend the following action: %s",
                rootCause, suggestedFix
            );
        } else {
            confidence = 20;
            category = "Unclassified";
            rootCause = "Unable to determine root cause from the provided log.";
            suggestedFix = "Please review the log manually or contact support.";
            severity = "MEDIUM";
            customerUpdate = "We are investigating the pipeline failure and will provide an update shortly.";
        }

        AnalyzedLog result = AnalyzedLog.builder()
            .logText(logText)
            .category(category)
            .rootCause(rootCause)
            .suggestedFix(suggestedFix)
            .customerUpdate(customerUpdate)
            .severity(severity)
            .confidence(confidence)
            .build();

        return analyzedLogRepository.save(result);
    }

    public List<AnalyzedLog> getHistory() {
        return analyzedLogRepository.findTop50ByOrderByCreatedAtDesc();
    }
}
