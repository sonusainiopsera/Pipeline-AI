package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.dto.Responses.HistoryDetailDTO;
import com.opsera.pipelineassistant.model.AnalyzedLog;
import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceDetailTest {

    @Mock
    private AnalyzedLogRepository analyzedLogRepository;

    @Mock
    private LogSanitizer logSanitizer;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @InjectMocks
    private AnalysisService analysisService;

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void historyDetail_returnsFullDetailDTOForExistingRecord() {
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30);
        AnalyzedLog record = AnalyzedLog.builder()
                .id(1L)
                .logText("raw pipeline log")
                .category("Memory")
                .rootCause("Heap exhausted")
                .suggestedFix("Increase -Xmx")
                .customerUpdate("We identified a memory issue.")
                .severity("HIGH")
                .confidence(95)
                .createdAt(createdAt)
                .build();

        when(analyzedLogRepository.findById(1L)).thenReturn(Optional.of(record));
        when(logSanitizer.sanitize("raw pipeline log")).thenReturn("sanitized pipeline log");

        HistoryDetailDTO result = analysisService.historyDetail(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.logText()).isEqualTo("sanitized pipeline log");
        assertThat(result.detectedCategory()).isEqualTo("Memory");
        assertThat(result.rootCause()).isEqualTo("Heap exhausted");
        assertThat(result.suggestedFix()).isEqualTo("Increase -Xmx");
        assertThat(result.customerUpdate()).isEqualTo("We identified a memory issue.");
        assertThat(result.severity()).isEqualTo("HIGH");
        assertThat(result.confidence()).isEqualTo(95);
        assertThat(result.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void historyDetail_sanitizesLogTextBeforeReturning() {
        AnalyzedLog record = AnalyzedLog.builder()
                .id(2L)
                .logText("sensitive-token=abc123")
                .category("Auth")
                .build();

        when(analyzedLogRepository.findById(2L)).thenReturn(Optional.of(record));
        when(logSanitizer.sanitize("sensitive-token=abc123")).thenReturn("[REDACTED]");

        HistoryDetailDTO result = analysisService.historyDetail(2L);

        verify(logSanitizer).sanitize("sensitive-token=abc123");
        assertThat(result.logText()).isEqualTo("[REDACTED]");
    }

    @Test
    void historyDetail_returnsNullLogTextWhenEntityLogTextIsNull() {
        AnalyzedLog record = AnalyzedLog.builder()
                .id(3L)
                .logText(null)
                .category("Unclassified")
                .build();

        when(analyzedLogRepository.findById(3L)).thenReturn(Optional.of(record));

        HistoryDetailDTO result = analysisService.historyDetail(3L);

        assertThat(result.logText()).isNull();
    }

    // ── 404 path ──────────────────────────────────────────────────────────────

    @Test
    void historyDetail_throws404WhenRecordNotFound() {
        when(analyzedLogRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analysisService.historyDetail(999L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
                    assertThat(rse.getReason()).isEqualTo("Analysis record not found");
                });
    }

    @Test
    void historyDetail_notFoundMessageDoesNotRevealDeletion() {
        when(analyzedLogRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analysisService.historyDetail(42L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    // Must use generic message regardless of whether ID was deleted or never existed
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getReason()).isEqualTo("Analysis record not found");
                    assertThat(rse.getReason()).doesNotContain("deleted");
                    assertThat(rse.getReason()).doesNotContain("never");
                });
    }

    // ── Sanitizer failure fallback ─────────────────────────────────────────────

    @Test
    void historyDetail_returnsNullLogTextWhenSanitizerThrows() {
        AnalyzedLog record = AnalyzedLog.builder()
                .id(5L)
                .logText("some log text")
                .category("Docker")
                .build();

        when(analyzedLogRepository.findById(5L)).thenReturn(Optional.of(record));
        when(logSanitizer.sanitize("some log text")).thenThrow(new RuntimeException("sanitizer failed"));

        HistoryDetailDTO result = analysisService.historyDetail(5L);

        assertThat(result.logText()).isNull();
        assertThat(result.detectedCategory()).isEqualTo("Docker");
    }
}
