package com.opsera.pipelineassistant.dto;

import com.opsera.pipelineassistant.model.AnalyzedLog;

import java.time.LocalDateTime;
import java.util.List;

public final class Responses {

    private Responses() {}

    public record RegisterResponse(String message) {}

    public record RefreshResult(String accessToken, String refreshToken) {}

    public record LoginResponse(String email, String displayName, String role, boolean mfaRequired) {}

    public record LoginResult(String accessToken, String rawRefreshToken, LoginResponse profile, String challengeToken) {}

    public record MfaSetupResponse(String qrCodeUri, List<String> recoveryCodes) {}

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

    /**
     * Full projection of AnalyzedLog for the GET /api/history/{id} endpoint.
     * Includes logText (sanitized) and customerUpdate — excluded from the list view
     * under the Confidential data classification policy.
     */
    public record HistoryDetailDTO(
            Long id,
            String logText,
            String detectedCategory,
            String rootCause,
            String suggestedFix,
            String customerUpdate,
            String severity,
            Integer confidence,
            LocalDateTime createdAt
    ) {
        public static HistoryDetailDTO from(AnalyzedLog log, String sanitizedLogText) {
            return new HistoryDetailDTO(
                    log.getId(),
                    sanitizedLogText,
                    log.getCategory(),
                    log.getRootCause(),
                    log.getSuggestedFix(),
                    log.getCustomerUpdate(),
                    log.getSeverity(),
                    log.getConfidence(),
                    log.getCreatedAt()
            );
        }
    }

    /**
     * Response DTO for the POST /api/analyze endpoint.
     * Includes matchedPatterns — the keyword strings from the best-matching KB entry
     * that were found in the submitted log text. This field is computed on-the-fly
     * and is not persisted to the database.
     */
    public record AnalysisResponse(
            Long id,
            String category,
            String rootCause,
            String suggestedFix,
            String customerUpdate,
            String severity,
            Integer confidence,
            LocalDateTime createdAt,
            List<String> matchedPatterns
    ) {
        public static AnalysisResponse from(AnalyzedLog log) {
            return new AnalysisResponse(
                    log.getId(),
                    log.getCategory(),
                    log.getRootCause(),
                    log.getSuggestedFix(),
                    log.getCustomerUpdate(),
                    log.getSeverity(),
                    log.getConfidence(),
                    log.getCreatedAt(),
                    log.getMatchedPatterns() != null ? log.getMatchedPatterns() : List.of()
            );
        }
    }
}
