package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.analysis.PatternMatcher;
import com.opsera.pipelineassistant.analysis.ResponseTemplater;
import com.opsera.pipelineassistant.analysis.ScoringEngine;
import com.opsera.pipelineassistant.audit.AuditService;
import com.opsera.pipelineassistant.model.AnalyzedLog;
import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import com.opsera.pipelineassistant.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceSanitizationTest {

    @Mock private KnowledgeBaseService knowledgeBaseService;
    @Mock private AnalyzedLogRepository analyzedLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private LogSanitizer logSanitizer;
    @Mock private PatternMatcher patternMatcher;
    @Mock private ScoringEngine scoringEngine;
    @Mock private ResponseTemplater responseTemplater;
    @Mock private MeterRegistry meterRegistry;
    @Mock private AuditService auditService;

    @InjectMocks
    private AnalysisService analysisService;

    @BeforeEach
    void setUp() {
        lenient().when(knowledgeBaseService.getAllEntries()).thenReturn(Collections.emptyList());
        lenient().when(patternMatcher.match(anyString(), any())).thenReturn(Collections.emptyList());
        lenient().when(scoringEngine.calculateConfidence(0, 0)).thenReturn(20);
        lenient().when(responseTemplater.generateUnclassified()).thenReturn("Unclassified update");
        lenient().when(analyzedLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void analyze_callsSanitizeBeforePersistence() {
        String rawLog = "connect failed: password=topsecret123 at 192.168.1.1";
        String sanitizedLog = "connect failed: password=[PASSWORD_REDACTED] at [IP_REDACTED]";
        when(logSanitizer.sanitize(rawLog)).thenReturn(sanitizedLog);

        analysisService.analyze(rawLog);

        verify(logSanitizer).sanitize(rawLog);
        ArgumentCaptor<AnalyzedLog> captor = ArgumentCaptor.forClass(AnalyzedLog.class);
        verify(analyzedLogRepository).save(captor.capture());
        assertThat(captor.getValue().getLogText()).isEqualTo(sanitizedLog);
        assertThat(captor.getValue().getLogText()).doesNotContain("topsecret123");
        assertThat(captor.getValue().getLogText()).doesNotContain("192.168.1.1");
    }

    @Test
    void analyze_rawLogTextNeverReachesDatabase() {
        String rawLog = "auth error: Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig AKIA1234567890ABCDEF";
        String sanitizedLog = "auth error: [BEARER_TOKEN_REDACTED] [AWS_KEY_REDACTED]";
        when(logSanitizer.sanitize(rawLog)).thenReturn(sanitizedLog);

        analysisService.analyze(rawLog);

        ArgumentCaptor<AnalyzedLog> captor = ArgumentCaptor.forClass(AnalyzedLog.class);
        verify(analyzedLogRepository).save(captor.capture());
        assertThat(captor.getValue().getLogText()).isEqualTo(sanitizedLog);
        assertThat(captor.getValue().getLogText()).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
        assertThat(captor.getValue().getLogText()).doesNotContain("AKIA1234567890ABCDEF");
    }

    @Test
    void analyze_passesSanitizedTextToPatternMatcher() {
        String rawLog = "user@example.com api_key=secret123 build failed";
        String sanitizedLog = "[EMAIL_REDACTED] api_key=[API_KEY_REDACTED] build failed";
        when(logSanitizer.sanitize(rawLog)).thenReturn(sanitizedLog);

        analysisService.analyze(rawLog);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(patternMatcher).match(textCaptor.capture(), any());
        assertThat(textCaptor.getValue()).isEqualTo(sanitizedLog);
        assertThat(textCaptor.getValue()).doesNotContain("user@example.com");
        assertThat(textCaptor.getValue()).doesNotContain("secret123");
    }

    @Test
    void analyze_withNullSanitizedResult_usesEmptyString() {
        String rawLog = "some log text";
        when(logSanitizer.sanitize(rawLog)).thenReturn(null);

        analysisService.analyze(rawLog);

        ArgumentCaptor<AnalyzedLog> captor = ArgumentCaptor.forClass(AnalyzedLog.class);
        verify(analyzedLogRepository).save(captor.capture());
        assertThat(captor.getValue().getLogText()).isEmpty();
    }
}
