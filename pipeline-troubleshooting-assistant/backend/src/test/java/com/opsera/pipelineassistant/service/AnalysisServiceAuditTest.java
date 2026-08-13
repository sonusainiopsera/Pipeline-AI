package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.analysis.PatternMatcher;
import com.opsera.pipelineassistant.analysis.ResponseProperties;
import com.opsera.pipelineassistant.analysis.ResponseTemplater;
import com.opsera.pipelineassistant.analysis.ScoringEngine;
import com.opsera.pipelineassistant.analysis.ScoringProperties;
import com.opsera.pipelineassistant.audit.AuditService;
import com.opsera.pipelineassistant.model.AnalyzedLog;
import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceAuditTest {

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private AnalyzedLogRepository analyzedLogRepository;

    @Mock
    private LogSanitizer logSanitizer;

    @Spy
    private PatternMatcher patternMatcher;

    @Spy
    private ScoringEngine scoringEngine = new ScoringEngine(new ScoringProperties());

    @Spy
    private ResponseTemplater responseTemplater = new ResponseTemplater(new ResponseProperties());

    @Mock
    private AuditService auditService;

    private AnalysisService service;

    @BeforeEach
    void setUp() {
        service = new AnalysisService(
                knowledgeBaseService, analyzedLogRepository, logSanitizer,
                patternMatcher, scoringEngine, responseTemplater,
                new SimpleMeterRegistry(), auditService);

        lenient().when(logSanitizer.sanitize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(knowledgeBaseService.getAllEntries()).thenReturn(Collections.emptyList());
        lenient().when(analyzedLogRepository.save(any())).thenAnswer(inv -> {
            AnalyzedLog log = inv.getArgument(0);
            log.setId(42L);
            return log;
        });
    }

    @Test
    void analyze_callsLogEventWithAnalyzeActionAndAnalysisResourceType() {
        service.analyze("some log text");

        verify(auditService).logEvent(eq("ANALYZE"), eq("ANALYSIS"), any(), any());
    }

    @Test
    void analyze_resourceIdMatchesSavedAnalyzedLogId() {
        service.analyze("some log text");

        verify(auditService).logEvent(eq("ANALYZE"), eq("ANALYSIS"), eq("42"), any());
    }

    @Test
    void analyze_detailsContainCategoryConfidenceAndSeverity() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);

        service.analyze("some log text");

        verify(auditService).logEvent(eq("ANALYZE"), eq("ANALYSIS"), any(), captor.capture());
        Map<String, Object> details = captor.getValue();
        assertThat(details).containsKey("category");
        assertThat(details).containsKey("confidence");
        assertThat(details).containsKey("severity");
    }

    @Test
    void analyze_detailsDoNotContainLogText() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);

        service.analyze("sensitive log text with credentials");

        verify(auditService).logEvent(eq("ANALYZE"), eq("ANALYSIS"), any(), captor.capture());
        Map<String, Object> details = captor.getValue();
        assertThat(details).doesNotContainKey("logText");
    }

    @Test
    void analyze_auditExceptionDoesNotPropagateAndResultIsReturned() {
        doThrow(new RuntimeException("audit failure"))
                .when(auditService).logEvent(any(), any(), any(), any());

        AnalyzedLog result = service.analyze("some log text");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(42L);
    }

    @Test
    void analyze_auditIsCalledExactlyOncePerAnalysis() {
        service.analyze("first log");
        service.analyze("second log");

        verify(auditService, times(2)).logEvent(eq("ANALYZE"), eq("ANALYSIS"), any(), any());
    }
}
