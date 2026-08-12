package com.opsera.pipelineassistant.dto;

import com.opsera.pipelineassistant.model.AnalyzedLog;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryListDTOTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2024, 6, 15, 10, 30, 0);

    private AnalyzedLog buildLog() {
        return AnalyzedLog.builder()
                .id(42L)
                .logText("SENSITIVE pipeline log content with secrets")
                .category("Memory")
                .rootCause("Heap space exhausted")
                .suggestedFix("Increase -Xmx flag")
                .customerUpdate("We are investigating the issue")
                .severity("HIGH")
                .confidence(95)
                .build();
    }

    @Test
    void fromMapsIdCorrectly() {
        AnalyzedLog log = buildLog();
        Responses.HistoryListDTO dto = Responses.HistoryListDTO.from(log);
        assertThat(dto.id()).isEqualTo(42L);
    }

    @Test
    void fromMapsDetectedCategoryFromCategory() {
        AnalyzedLog log = buildLog();
        Responses.HistoryListDTO dto = Responses.HistoryListDTO.from(log);
        assertThat(dto.detectedCategory()).isEqualTo("Memory");
    }

    @Test
    void fromMapsRootCauseCorrectly() {
        AnalyzedLog log = buildLog();
        Responses.HistoryListDTO dto = Responses.HistoryListDTO.from(log);
        assertThat(dto.rootCause()).isEqualTo("Heap space exhausted");
    }

    @Test
    void fromMapsSuggestedFixCorrectly() {
        AnalyzedLog log = buildLog();
        Responses.HistoryListDTO dto = Responses.HistoryListDTO.from(log);
        assertThat(dto.suggestedFix()).isEqualTo("Increase -Xmx flag");
    }

    @Test
    void fromMapsSeverityCorrectly() {
        AnalyzedLog log = buildLog();
        Responses.HistoryListDTO dto = Responses.HistoryListDTO.from(log);
        assertThat(dto.severity()).isEqualTo("HIGH");
    }

    @Test
    void fromMapsConfidenceCorrectly() {
        AnalyzedLog log = buildLog();
        Responses.HistoryListDTO dto = Responses.HistoryListDTO.from(log);
        assertThat(dto.confidence()).isEqualTo(95);
    }

    @Test
    void fromMapsCreatedAtCorrectly() {
        AnalyzedLog log = AnalyzedLog.builder()
                .id(1L)
                .category("Network")
                .rootCause("Connection refused")
                .suggestedFix("Check firewall")
                .severity("HIGH")
                .confidence(88)
                .logText("raw log")
                .customerUpdate("investigating")
                .build();
        // createdAt is set by @CreationTimestamp — simulate via a builder workaround isn't possible
        // so we test that null createdAt passes through without error
        Responses.HistoryListDTO dto = Responses.HistoryListDTO.from(log);
        assertThat(dto.createdAt()).isNull();
    }

    @Test
    void dtoDoesNotExposeLogText() {
        // HistoryListDTO is a record — verify logText is not a component by checking
        // that the record only has the 7 expected components
        var components = Responses.HistoryListDTO.class.getRecordComponents();
        assertThat(components).hasSize(7);

        var componentNames = java.util.Arrays.stream(components)
                .map(c -> c.getName())
                .toList();
        assertThat(componentNames).doesNotContain("logText");
        assertThat(componentNames).doesNotContain("customerUpdate");
    }

    @Test
    void dtoDoesNotExposeCustomerUpdate() {
        var components = Responses.HistoryListDTO.class.getRecordComponents();
        var componentNames = java.util.Arrays.stream(components)
                .map(c -> c.getName())
                .toList();
        assertThat(componentNames).doesNotContain("customerUpdate");
    }

    @Test
    void dtoHasAllSevenExpectedFields() {
        var components = Responses.HistoryListDTO.class.getRecordComponents();
        var componentNames = java.util.Arrays.stream(components)
                .map(c -> c.getName())
                .toList();
        assertThat(componentNames).containsExactly(
                "id", "detectedCategory", "rootCause", "suggestedFix",
                "severity", "confidence", "createdAt");
    }

    @Test
    void fromHandlesNullLogTextWithoutError() {
        AnalyzedLog log = AnalyzedLog.builder()
                .id(5L)
                .logText(null)
                .category("Docker")
                .rootCause("Image pull failed")
                .suggestedFix("Check credentials")
                .customerUpdate(null)
                .severity("HIGH")
                .confidence(82)
                .build();
        Responses.HistoryListDTO dto = Responses.HistoryListDTO.from(log);
        assertThat(dto.id()).isEqualTo(5L);
        assertThat(dto.detectedCategory()).isEqualTo("Docker");
    }
}
