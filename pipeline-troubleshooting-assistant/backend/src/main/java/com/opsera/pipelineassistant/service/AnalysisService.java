package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.analysis.PatternMatcher;
import com.opsera.pipelineassistant.analysis.ScoredMatch;
import com.opsera.pipelineassistant.analysis.ScoringEngine;
import com.opsera.pipelineassistant.model.AnalyzedLog;
import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import com.opsera.pipelineassistant.repository.ErrorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

    private final ErrorRepository errorRepository;
    private final AnalyzedLogRepository analyzedLogRepository;
    private final LogSanitizer logSanitizer;
    private final PatternMatcher patternMatcher;
    private final ScoringEngine scoringEngine;

    public AnalyzedLog analyze(String logText) {
        String sanitizedLog;
        try {
            sanitizedLog = logSanitizer.sanitize(logText);
            if (sanitizedLog == null) {
                sanitizedLog = "";
            }
        } catch (Exception e) {
            log.warn("Log sanitization failed, aborting to prevent raw log persistence");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Analysis failed. Please try again.");
        }

        List<ErrorKnowledgeBase> patterns = errorRepository.findAll();
        List<ScoredMatch> scored = patternMatcher.match(sanitizedLog, patterns);

        ErrorKnowledgeBase bestMatch = null;
        int bestMatchCount = 0;

        for (ScoredMatch sm : scored) {
            if (sm.score() > bestMatchCount) {
                bestMatchCount = sm.score();
                bestMatch = sm.entry();
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
            confidence = scoringEngine.calculateConfidence(bestMatchCount, totalKeywords);
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
            confidence = scoringEngine.calculateConfidence(0, 0);
            category = "Unclassified";
            rootCause = "Unable to determine root cause from the provided log.";
            suggestedFix = "Please review the log manually or contact support.";
            severity = "MEDIUM";
            customerUpdate = "We are investigating the pipeline failure and will provide an update shortly.";
        }

        AnalyzedLog result = AnalyzedLog.builder()
            .logText(sanitizedLog)
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
