package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.analysis.PatternMatcher;
import com.opsera.pipelineassistant.analysis.ResponseProperties;
import com.opsera.pipelineassistant.analysis.ResponseTemplater;
import com.opsera.pipelineassistant.analysis.ScoringEngine;
import com.opsera.pipelineassistant.analysis.ScoringProperties;
import com.opsera.pipelineassistant.model.AnalyzedLog;
import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import com.opsera.pipelineassistant.repository.ErrorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    @Mock
    private ErrorRepository errorRepository;

    @Mock
    private AnalyzedLogRepository analyzedLogRepository;

    @Mock
    private LogSanitizer logSanitizer;

    // Use a real PatternMatcher so tests exercise the actual matching logic.
    // PatternMatcher has no dependencies, so @Spy creates a real instance.
    @Spy
    private PatternMatcher patternMatcher;

    // Use a real ScoringEngine with default configuration so confidence values
    // match the previously hardcoded constants (55, 43, 98).
    @Spy
    private ScoringEngine scoringEngine = new ScoringEngine(new ScoringProperties());

    // Use a real ResponseTemplater with default configuration so customerUpdate strings
    // match the previously hardcoded template format.
    @Spy
    private ResponseTemplater responseTemplater = new ResponseTemplater(new ResponseProperties());

    @InjectMocks
    private AnalysisService analysisService;

    @BeforeEach
    void setUp() {
        // Pass-through stub — existing tests are unaffected; sanitize returns the input unchanged.
        // lenient() prevents UnnecessaryStubbingException when tests also register specific stubs.
        lenient().when(logSanitizer.sanitize(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ErrorKnowledgeBase buildKnowledgeBaseEntry(String errorPattern,
                                                        String category,
                                                        String rootCause,
                                                        String solution,
                                                        String severity) {
        return ErrorKnowledgeBase.builder()
                .id(1L)
                .errorPattern(errorPattern)
                .category(category)
                .rootCause(rootCause)
                .solution(solution)
                .severity(severity)
                .build();
    }

    // ── 1. Empty knowledge base ──────────────────────────────────────────────
    @Test
    void shouldReturnUnclassifiedWhenKnowledgeBaseIsEmpty() {
        when(errorRepository.findAll()).thenReturn(Collections.emptyList());
        when(analyzedLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalyzedLog result = analysisService.analyze("some log text with no matching patterns");

        assertThat(result.getCategory()).isEqualTo("Unclassified");
        assertThat(result.getConfidence()).isEqualTo(20);
        assertThat(result.getRootCause()).isEqualTo("Unable to determine root cause from the provided log.");
        assertThat(result.getSuggestedFix()).isEqualTo("Please review the log manually or contact support.");
    }

    // ── 2. All keywords in a single pattern match ────────────────────────────
    // 3 keywords, all match: min(98, 55 + 3*43/3) = min(98, 98) = 98
    @Test
    void shouldMatchSinglePatternWithAllKeywords() {
        ErrorKnowledgeBase entry = buildKnowledgeBaseEntry(
                "OutOfMemoryError,heap space,java.lang",
                "Memory", "Heap space exhausted", "Increase -Xmx", "HIGH");
        when(errorRepository.findAll()).thenReturn(List.of(entry));
        when(analyzedLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalyzedLog result = analysisService.analyze("outofmemoryerror heap space java.lang detected");

        assertThat(result.getCategory()).isEqualTo("Memory");
        assertThat(result.getConfidence()).isEqualTo(98);
    }

    // ── 3. Partial keyword match confidence calculation ──────────────────────
    // 4 keywords, 2 match: 2*43/4 = 21 (integer div), confidence = min(98, 55+21) = 76
    @Test
    void shouldCalculatePartialMatchConfidence() {
        ErrorKnowledgeBase entry = buildKnowledgeBaseEntry(
                "alpha,beta,gamma,delta",
                "Network", "Network failure", "Check connectivity", "HIGH");
        when(errorRepository.findAll()).thenReturn(List.of(entry));
        when(analyzedLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalyzedLog result = analysisService.analyze("alpha and beta are present but not the rest");

        assertThat(result.getConfidence()).isEqualTo(76);
    }

    // ── 4. Best match selected from multiple entries ─────────────────────────
    @Test
    void shouldSelectBestMatchFromMultipleEntries() {
        ErrorKnowledgeBase lowScore = buildKnowledgeBaseEntry(
                "token1,token2,token3", "Category-Low", "Low cause", "Low fix", "LOW");
        ErrorKnowledgeBase highScore = buildKnowledgeBaseEntry(
                "alpha,beta,gamma", "Category-High", "High cause", "High fix", "HIGH");
        ErrorKnowledgeBase noMatch = buildKnowledgeBaseEntry(
                "xyz,uvw", "Category-None", "None cause", "None fix", "LOW");

        // Log matches 1 keyword from lowScore and all 3 from highScore
        when(errorRepository.findAll()).thenReturn(List.of(lowScore, highScore, noMatch));
        when(analyzedLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalyzedLog result = analysisService.analyze("token1 alpha beta gamma unrelated");

        assertThat(result.getCategory()).isEqualTo("Category-High");
    }

    // ── 5. Confidence capped at 98 ────────────────────────────────────────────
    // 1 keyword, matches: min(98, 55 + 1*43/1) = min(98, 98) = 98
    @Test
    void shouldCapConfidenceAt98() {
        ErrorKnowledgeBase entry = buildKnowledgeBaseEntry(
                "error", "Errors", "An error occurred", "Fix the error", "MEDIUM");
        when(errorRepository.findAll()).thenReturn(List.of(entry));
        when(analyzedLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalyzedLog result = analysisService.analyze("error in pipeline");

        assertThat(result.getConfidence()).isLessThanOrEqualTo(98);
        assertThat(result.getConfidence()).isEqualTo(98);
    }

    // ── 6. Case-insensitive keyword matching ──────────────────────────────────
    @Test
    void shouldHandleCaseInsensitiveMatching() {
        ErrorKnowledgeBase entry = buildKnowledgeBaseEntry(
                "OUTOFMEMORY,HEAP",
                "Memory", "OOM detected", "Increase heap", "HIGH");
        when(errorRepository.findAll()).thenReturn(List.of(entry));
        when(analyzedLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalyzedLog result = analysisService.analyze("outofmemory heap error encountered");

        assertThat(result.getCategory()).isEqualTo("Memory");
        // 2 keywords, all match → min(98, 55 + 2*43/2) = min(98, 98) = 98
        assertThat(result.getConfidence()).isEqualTo(98);
    }

    // ── 7. Whitespace around commas in errorPattern is trimmed ────────────────
    @Test
    void shouldHandleWhitespaceInPatternKeywords() {
        ErrorKnowledgeBase entry = buildKnowledgeBaseEntry(
                " alpha , beta , gamma ",
                "Network", "Network issue", "Fix network", "HIGH");
        when(errorRepository.findAll()).thenReturn(List.of(entry));
        when(analyzedLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalyzedLog result = analysisService.analyze("alpha beta gamma detected in log");

        assertThat(result.getCategory()).isEqualTo("Network");
        assertThat(result.getConfidence()).isEqualTo(98);
    }

    // ── 8. Customer update template contains root cause and fix ───────────────
    @Test
    void shouldVerifyCustomerUpdateTemplateFormat() {
        ErrorKnowledgeBase entry = buildKnowledgeBaseEntry(
                "keyword",
                "TestCategory", "The specific root cause", "The specific recommended fix", "HIGH");
        when(errorRepository.findAll()).thenReturn(List.of(entry));
        when(analyzedLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalyzedLog result = analysisService.analyze("keyword found in pipeline log");

        assertThat(result.getCustomerUpdate())
                .contains("The specific root cause")
                .contains("The specific recommended fix");
    }

    // ── 9. Persistence: save() called once with correct fields ────────────────
    @Test
    void shouldPersistAnalyzedLog() {
        ErrorKnowledgeBase entry = buildKnowledgeBaseEntry(
                "docker,build", "Docker", "Docker build failed", "Fix Dockerfile", "HIGH");
        when(errorRepository.findAll()).thenReturn(List.of(entry));
        ArgumentCaptor<AnalyzedLog> captor = ArgumentCaptor.forClass(AnalyzedLog.class);
        when(analyzedLogRepository.save(captor.capture()))
                .thenReturn(AnalyzedLog.builder().build());

        analysisService.analyze("docker build error encountered");

        verify(analyzedLogRepository, times(1)).save(any(AnalyzedLog.class));
        AnalyzedLog saved = captor.getValue();
        assertThat(saved.getCategory()).isEqualTo("Docker");
        assertThat(saved.getLogText()).isEqualTo("docker build error encountered");
        assertThat(saved.getSeverity()).isEqualTo("HIGH");
        assertThat(saved.getRootCause()).isEqualTo("Docker build failed");
        assertThat(saved.getSuggestedFix()).isEqualTo("Fix Dockerfile");
    }

    // ── 10. Single entry, no keyword matches → unclassified ──────────────────
    @Test
    void shouldReturnUnclassifiedWhenNoKeywordsMatchInSingleEntry() {
        ErrorKnowledgeBase entry = buildKnowledgeBaseEntry(
                "xyz,uvw,pqr", "Network", "Network issue", "Fix network", "HIGH");
        when(errorRepository.findAll()).thenReturn(List.of(entry));
        when(analyzedLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalyzedLog result = analysisService.analyze("completely unrelated log message here");

        assertThat(result.getCategory()).isEqualTo("Unclassified");
        assertThat(result.getConfidence()).isEqualTo(20);
    }

    // ── 11. Single-keyword pattern ────────────────────────────────────────────
    // 1 keyword, matches: min(98, 55 + 1*43/1) = 98
    @Test
    void shouldHandleSingleKeywordPattern() {
        ErrorKnowledgeBase entry = buildKnowledgeBaseEntry(
                "timeout",
                "Timeout", "Request timed out", "Increase timeout threshold", "MEDIUM");
        when(errorRepository.findAll()).thenReturn(List.of(entry));
        when(analyzedLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalyzedLog result = analysisService.analyze("connection timeout occurred during build");

        assertThat(result.getCategory()).isEqualTo("Timeout");
        assertThat(result.getConfidence()).isEqualTo(98);
    }

    // ── 12. Integer division truncation in confidence formula ────────────────
    // 3 keywords, 1 match: 1*43/3 = 14 (integer div), confidence = min(98, 55+14) = 69
    @Test
    void shouldVerifyIntegerDivisionTruncationInConfidenceFormula() {
        ErrorKnowledgeBase entry = buildKnowledgeBaseEntry(
                "alpha,beta,gamma",
                "Network", "Network issue", "Fix network", "HIGH");
        when(errorRepository.findAll()).thenReturn(List.of(entry));
        when(analyzedLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalyzedLog result = analysisService.analyze("alpha is present but beta and gamma are not");

        // 1*43/3 = 14 due to integer division → confidence = 55 + 14 = 69
        assertThat(result.getConfidence()).isEqualTo(69);
    }

    // ── 13. Unclassified confidence is 20, not the 55 base ───────────────────
    @Test
    void shouldUseConfidence20ForUnclassifiedNotBase55() {
        when(errorRepository.findAll()).thenReturn(Collections.emptyList());
        when(analyzedLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalyzedLog result = analysisService.analyze("log with no matching patterns");

        assertThat(result.getConfidence()).isEqualTo(20);
        assertThat(result.getConfidence()).isNotEqualTo(55);
        assertThat(result.getSeverity()).isEqualTo("MEDIUM");
    }

    // ── 14. Ties in match count go to the first entry (strict > not >=) ──────
    @Test
    void shouldReturnFirstEntryWhenScoresAreTied() {
        ErrorKnowledgeBase first = buildKnowledgeBaseEntry(
                "alpha,beta", "First", "First root cause", "First fix", "LOW");
        ErrorKnowledgeBase second = buildKnowledgeBaseEntry(
                "alpha,beta", "Second", "Second root cause", "Second fix", "HIGH");
        when(errorRepository.findAll()).thenReturn(List.of(first, second));
        when(analyzedLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnalyzedLog result = analysisService.analyze("alpha beta present in log");

        // Both score 2; strict > means the first entry retains best-match status on ties
        assertThat(result.getCategory()).isEqualTo("First");
    }

    // ── 15. LogSanitizer is called exactly once with the raw input ────────────
    @Test
    void shouldCallLogSanitizerExactlyOnceWithRawInput() {
        String rawInput = "pipeline log with potential secrets";
        when(errorRepository.findAll()).thenReturn(Collections.emptyList());
        when(analyzedLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        analysisService.analyze(rawInput);

        verify(logSanitizer, times(1)).sanitize(rawInput);
    }

    // ── 16. Sanitized text (not raw) is persisted to the database ─────────────
    @Test
    void shouldPersistSanitizedTextNotRawLog() {
        String rawLog = "pipeline log with AKIAIOSFODNN7EXAMPLE secret";
        String sanitizedOutput = "pipeline log with [AWS_KEY_REDACTED] secret";
        when(logSanitizer.sanitize(rawLog)).thenReturn(sanitizedOutput);

        when(errorRepository.findAll()).thenReturn(Collections.emptyList());
        ArgumentCaptor<AnalyzedLog> captor = ArgumentCaptor.forClass(AnalyzedLog.class);
        when(analyzedLogRepository.save(captor.capture())).thenReturn(AnalyzedLog.builder().build());

        analysisService.analyze(rawLog);

        assertThat(captor.getValue().getLogText()).isEqualTo(sanitizedOutput);
        assertThat(captor.getValue().getLogText()).isNotEqualTo(rawLog);
    }
}
