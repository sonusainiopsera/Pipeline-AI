package com.opsera.pipelineassistant.scheduler;

import com.opsera.pipelineassistant.audit.AuditService;
import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import com.opsera.pipelineassistant.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * Daily purge scheduler that enforces data retention policies.
 *
 * Analyzed logs older than {@code retention.analyzed-logs-days} (default 90) and
 * audit logs older than {@code retention.audit-logs-days} (default 365) are physically
 * deleted at 02:00 UTC. The entire purge body is wrapped in a try-catch so that any
 * unexpected exception is logged at ERROR level and the scheduler thread is not killed.
 *
 * When records are deleted, a PURGE audit event is written with the deletion counts.
 * When no records are eligible, the run is a no-op logged at INFO level.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataRetentionScheduler {

    private final AnalyzedLogRepository analyzedLogRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    @Value("${retention.analyzed-logs-days:90}")
    private int analyzedLogRetentionDays;

    @Value("${retention.audit-logs-days:365}")
    private int auditLogRetentionDays;

    /**
     * Purges expired records from analyzed_logs and audit_logs tables.
     * Runs daily at 02:00 UTC. Both deletes are batched in a single transaction.
     * An audit event is created after a successful purge of one or more records.
     * If zero records are eligible, logs INFO and skips the audit event.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    @Transactional
    public void purgeExpiredRecords() {
        log.info("Data retention purge started: analyzedRetentionDays={}, auditRetentionDays={}",
                analyzedLogRetentionDays, auditLogRetentionDays);
        try {
            LocalDateTime analyzedCutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(analyzedLogRetentionDays);
            LocalDateTime auditCutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(auditLogRetentionDays);

            int analyzedDeleted = analyzedLogRepository.deleteByCreatedAtBefore(analyzedCutoff);
            log.debug("Purged {} analyzed_logs records older than {} days", analyzedDeleted, analyzedLogRetentionDays);

            int auditDeleted = auditLogRepository.deleteByCreatedAtBefore(auditCutoff);
            log.debug("Purged {} audit_logs records older than {} days", auditDeleted, auditLogRetentionDays);

            if (analyzedDeleted == 0 && auditDeleted == 0) {
                log.info("Data retention purge: no records eligible for deletion");
                return;
            }

            Map<String, Object> details = new HashMap<>();
            details.put("analyzedLogsDeleted", analyzedDeleted);
            details.put("auditLogsDeleted", auditDeleted);
            auditService.logEvent("PURGE", "ANALYSIS", null, details);

            log.info("Data retention purge completed: analyzedLogsDeleted={}, auditLogsDeleted={}",
                    analyzedDeleted, auditDeleted);
        } catch (Exception e) {
            log.error("Data retention purge failed: {}", e.getMessage(), e);
        }
    }
}
