package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.analysis.PatternMatcher;
import com.opsera.pipelineassistant.analysis.ResponseTemplater;
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
    private final ResponseTemplater responseTemplater;

    public AnalyzedLog analyze(String logText) {
        log.info("Starting analysis, logTextLength={}", logText != null ? logText.length() : 0);

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

        // 1. Fetch knowledge base entries
        List<ErrorKnowledgeBase> entries = errorRepository.findAll();

        // 2. Score each entry against the sanitized log
        List<ScoredMatch> scored = patternMatcher.match(sanitizedLog, entries);

        // 3. Select best match (first-wins on ties via strict >)
        ScoredMatch best = scored.stream()
                .filter(sm -> sm.score() > 0)
                .reduce((a, b) -> b.score() > a.score() ? b : a)
                .orElse(null);

        int confidence;
        String category;
        String rootCause;
        String suggestedFix;
        String severity;
        String customerUpdate;

        if (best != null) {
            // 4-5. Delegate confidence and response to extracted beans
            confidence = scoringEngine.calculateConfidence(best.score(), best.totalPatterns());
            category = best.entry().getCategory();
            rootCause = best.entry().getRootCause();
            suggestedFix = best.entry().getSolution();
            severity = best.entry().getSeverity();
            customerUpdate = responseTemplater.generate(category, rootCause, suggestedFix);
        } else {
            confidence = scoringEngine.calculateConfidence(0, 0);
            category = "Unclassified";
            rootCause = "Unable to determine root cause from the provided log.";
            suggestedFix = "Please review the log manually or contact support.";
            severity = "MEDIUM";
            customerUpdate = responseTemplater.generateUnclassified();
        }

        // 6. Build entity and persist
        AnalyzedLog result = AnalyzedLog.builder()
                .logText(sanitizedLog)
                .category(category)
                .rootCause(rootCause)
                .suggestedFix(suggestedFix)
                .customerUpdate(customerUpdate)
                .severity(severity)
                .confidence(confidence)
                .build();

        log.info("Analysis complete, category={}, confidence={}", category, confidence);
        return analyzedLogRepository.save(result);
    }

    public List<AnalyzedLog> getHistory() {
        return analyzedLogRepository.findTop50ByOrderByCreatedAtDesc();
    }
}
