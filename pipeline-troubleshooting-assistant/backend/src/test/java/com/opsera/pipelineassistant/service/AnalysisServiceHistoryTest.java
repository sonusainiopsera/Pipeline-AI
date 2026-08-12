package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.analysis.PatternMatcher;
import com.opsera.pipelineassistant.analysis.ResponseProperties;
import com.opsera.pipelineassistant.analysis.ResponseTemplater;
import com.opsera.pipelineassistant.analysis.ScoringEngine;
import com.opsera.pipelineassistant.analysis.ScoringProperties;
import com.opsera.pipelineassistant.dto.Responses.HistoryListDTO;
import com.opsera.pipelineassistant.model.AnalyzedLog;
import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AnalysisService.getHistory(Pageable) verifying correct delegation
 * to the repository, DTO mapping, and page metadata preservation.
 */
@ExtendWith(MockitoExtension.class)
class AnalysisServiceHistoryTest {

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
    private MeterRegistry meterRegistry;

    @InjectMocks
    private AnalysisService analysisService;

    private AnalyzedLog buildLog(Long id, String category) {
        return AnalyzedLog.builder()
                .id(id)
                .logText("sample log for " + category)
                .category(category)
                .rootCause("Root cause for " + category)
                .suggestedFix("Fix for " + category)
                .customerUpdate("Update for " + category)
                .severity("HIGH")
                .confidence(85)
                .build();
    }

    @Test
    void passesPageableThroughToRepository() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(analyzedLogRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of()));

        analysisService.getHistory(pageable);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(analyzedLogRepository).findAllByOrderByCreatedAtDesc(captor.capture());
        assertThat(captor.getValue()).isEqualTo(pageable);
    }

    @Test
    void mapsAnalyzedLogToHistoryListDTO() {
        AnalyzedLog log = buildLog(1L, "Memory");
        Pageable pageable = PageRequest.of(0, 20);
        when(analyzedLogRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(log)));

        Page<HistoryListDTO> result = analysisService.getHistory(pageable);

        assertThat(result.getContent()).hasSize(1);
        HistoryListDTO dto = result.getContent().get(0);
        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.detectedCategory()).isEqualTo("Memory");
        assertThat(dto.rootCause()).isEqualTo("Root cause for Memory");
        assertThat(dto.suggestedFix()).isEqualTo("Fix for Memory");
        assertThat(dto.severity()).isEqualTo("HIGH");
        assertThat(dto.confidence()).isEqualTo(85);
    }

    @Test
    void dtoHasExactlySevenFields() {
        assertThat(HistoryListDTO.class.getRecordComponents()).hasSize(7);
    }

    @Test
    void preservesPageMetadata() {
        List<AnalyzedLog> logs = List.of(buildLog(1L, "Memory"), buildLog(2L, "Network"));
        Pageable pageable = PageRequest.of(1, 2);
        Page<AnalyzedLog> repoPage = new PageImpl<>(logs, pageable, 10L);
        when(analyzedLogRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(repoPage);

        Page<HistoryListDTO> result = analysisService.getHistory(pageable);

        assertThat(result.getTotalElements()).isEqualTo(10L);
        assertThat(result.getTotalPages()).isEqualTo(5);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void returnsEmptyPageWhenRepositoryIsEmpty() {
        Pageable pageable = PageRequest.of(0, 20);
        when(analyzedLogRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of()));

        Page<HistoryListDTO> result = analysisService.getHistory(pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0L);
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void mapsMultipleLogsToCorrectDTOs() {
        AnalyzedLog log1 = buildLog(1L, "Memory");
        AnalyzedLog log2 = buildLog(2L, "Docker");
        AnalyzedLog log3 = buildLog(3L, "Network");
        Pageable pageable = PageRequest.of(0, 20);
        when(analyzedLogRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(log1, log2, log3)));

        Page<HistoryListDTO> result = analysisService.getHistory(pageable);

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).detectedCategory()).isEqualTo("Memory");
        assertThat(result.getContent().get(1).detectedCategory()).isEqualTo("Docker");
        assertThat(result.getContent().get(2).detectedCategory()).isEqualTo("Network");
    }
}
