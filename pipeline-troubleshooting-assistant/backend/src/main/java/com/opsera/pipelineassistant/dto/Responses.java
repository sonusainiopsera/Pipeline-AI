package com.opsera.pipelineassistant.dto;

import com.opsera.pipelineassistant.model.AnalyzedLog;

import java.time.LocalDateTime;

public final class Responses {

    private Responses() {}

    /**
     * Lightweight projection of AnalyzedLog for the history list endpoint.
     * Intentionally excludes logText and customerUpdate per data classification policy
     * (pipeline logs are Confidential).
     */
    public record HistoryListDTO(
            Long id,
            String detectedCategory,
            String rootCause,
            String suggestedFix,
            String severity,
            Integer confidence,
            LocalDateTime createdAt
    ) {
        public static HistoryListDTO from(AnalyzedLog log) {
            return new HistoryListDTO(
                    log.getId(),
                    log.getCategory(),
                    log.getRootCause(),
                    log.getSuggestedFix(),
                    log.getSeverity(),
                    log.getConfidence(),
                    log.getCreatedAt()
            );
        }
    }
}
