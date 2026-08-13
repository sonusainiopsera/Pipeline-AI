package com.opsera.pipelineassistant.dto;

import com.opsera.pipelineassistant.model.AnalyzedLog;
import com.opsera.pipelineassistant.model.AuditLog;
import com.opsera.pipelineassistant.model.RefreshToken;
import com.opsera.pipelineassistant.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class Responses {

    private Responses() {}

    public record CategoryStat(String category, long count, double percentage) {}

    public record AuditLogDTO(
            Long id,
            String actorEmail,
            String action,
            String resourceType,
            String resourceId,
            Map<String, Object> details,
            String ipAddress,
            LocalDateTime createdAt
    ) {
        public static AuditLogDTO from(AuditLog log) {
            return new AuditLogDTO(
                    log.getId(),
                    log.getActorEmail(),
                    log.getAction(),
                    log.getResourceType(),
                    log.getResourceId(),
                    log.getDetails(),
                    log.getIpAddress(),
                    log.getCreatedAt()
            );
        }
    }

    public record DashboardResponse(
            long totalErrors,
            long analyzedLogs,
            String mostCommonIssue,
            Map<String, Long> categoryBreakdown,
            int averageConfidence,
            long analysesLast7Days,
            long analysesLast30Days,
            List<CategoryStat> topCategories
    ) {}

    public record UserProfileDTO(
            String displayName,
            String email,
            String role,
            boolean mfaEnabled,
            LocalDateTime createdAt
    ) {
        public static UserProfileDTO from(User user) {
            return new UserProfileDTO(
                    user.getDisplayName(),
                    user.getEmail(),
                    user.getRole().name(),
                    Boolean.TRUE.equals(user.getMfaEnabled()),
                    user.getCreatedAt()
            );
        }
    }

    public record SessionDTO(
            String sessionId,
            LocalDateTime createdAt,
            LocalDateTime lastUsedAt,
            String ipAddress,
            boolean isCurrent
    ) {
        public static SessionDTO from(RefreshToken token) {
            return new SessionDTO(
                    token.getId().toString(),
                    token.getCreatedAt(),
                    token.getCreatedAt(),
                    null,
                    false
            );
        }
    }

    public record RegisterResponse(String message, String verificationUrl) {}

    /** Outcome of registration, including the verification URL for local/dev clients. */
    public record RegistrationResult(User user, String verificationUrl) {}

    public record RefreshResult(String accessToken, String refreshToken) {}

    public record LoginResponse(
            String email,
            String displayName,
            String role,
            boolean mfaRequired,
            boolean mfaEnabled
    ) {
        public static LoginResponse from(User user, boolean mfaRequired) {
            return new LoginResponse(
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getRole().name(),
                    mfaRequired,
                    Boolean.TRUE.equals(user.getMfaEnabled())
            );
        }
    }

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
            List<String> matchedPatterns,
            boolean sanitized
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
                    log.getMatchedPatterns() != null ? log.getMatchedPatterns() : List.of(),
                    true
            );
        }
    }
}
