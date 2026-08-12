package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.analysis.PatternMatcher;
import com.opsera.pipelineassistant.analysis.ResponseTemplater;
import com.opsera.pipelineassistant.analysis.ScoredMatch;
import com.opsera.pipelineassistant.analysis.ScoringEngine;
import com.opsera.pipelineassistant.dto.Responses.HistoryListDTO;
import com.opsera.pipelineassistant.model.AnalyzedLog;
import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final AnalyzedLogRepository analyzedLogRepository;
    private final LogSanitizer logSanitizer;
    private final PatternMatcher patternMatcher;
    private final ScoringEngine scoringEngine;
    private final ResponseTemplater responseTemplater;
    private final MeterRegistry meterRegistry;

    public AnalyzedLog analyze(String logText) {
        log.info("Starting analysis, logTextLength={}", logText != null ? logText.length() : 0);

        // Start timer using system clock — does not touch the registry so it is safe
        // even when MeterRegistry is unavailable in test contexts.
        Timer.Sample sample = Timer.start(Clock.SYSTEM);

        try {
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

            // 1. Fetch knowledge base entries — served from Caffeine cache after first call
            List<ErrorKnowledgeBase> entries = knowledgeBaseService.getAllEntries();

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
            List<String> matchedPatterns;

            if (best != null) {
                // 4-5. Delegate confidence and response to extracted beans
                confidence = scoringEngine.calculateConfidence(best.score(), best.totalPatterns());
                category = best.entry().getCategory();
                rootCause = best.entry().getRootCause();
                suggestedFix = best.entry().getSolution();
                severity = best.entry().getSeverity();
                customerUpdate = responseTemplater.generate(category, rootCause, suggestedFix);
                matchedPatterns = best.matchedPatterns();
            } else {
                confidence = scoringEngine.calculateConfidence(0, 0);
                category = "Unclassified";
                rootCause = "Unable to determine root cause from the provided log.";
                suggestedFix = "Please review the log manually or contact support.";
                severity = "MEDIUM";
                customerUpdate = responseTemplater.generateUnclassified();
                matchedPatterns = Collections.emptyList();
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
            result.setMatchedPatterns(matchedPatterns);

            // 7. Record analysis.requests counter with detected category tag
            try {
                meterRegistry.counter("analysis.requests", "category", category).increment();
            } catch (Exception metricEx) {
                log.warn("Failed to record analysis.requests metric: {}", metricEx.getMessage());
            }

            log.info("Analysis complete, category={}, confidence={}", category, confidence);
            return analyzedLogRepository.save(result);
        } finally {
            // Always record latency even when an exception propagates
            try {
                sample.stop(meterRegistry.timer("analysis.duration"));
            } catch (Exception metricEx) {
                log.warn("Failed to record analysis.duration metric: {}", metricEx.getMessage());
            }
        }
    }

    public Page<HistoryListDTO> getHistory(Pageable pageable) {
        return analyzedLogRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(HistoryListDTO::from);
    }
}
