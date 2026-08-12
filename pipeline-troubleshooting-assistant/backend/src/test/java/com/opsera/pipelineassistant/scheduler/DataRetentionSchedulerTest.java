package com.opsera.pipelineassistant.scheduler;

import com.opsera.pipelineassistant.audit.AuditService;
import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import com.opsera.pipelineassistant.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataRetentionSchedulerTest {

    @Mock
    private AnalyzedLogRepository analyzedLogRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private DataRetentionScheduler scheduler;

    @BeforeEach
    void injectRetentionDays() {
        ReflectionTestUtils.setField(scheduler, "analyzedLogRetentionDays", 90);
        ReflectionTestUtils.setField(scheduler, "auditLogRetentionDays", 365);
    }

    // ── Cutoff date calculation ───────────────────────────────────────────────

    @Test
    void purge_callsAnalyzedLogRepositoryWithCorrectCutoffDate() {
        when(analyzedLogRepository.deleteByCreatedAtBefore(any())).thenReturn(5);
        when(auditLogRepository.deleteByCreatedAtBefore(any())).thenReturn(0);

        LocalDateTime lower = LocalDateTime.now(ZoneOffset.UTC).minusDays(90).minusSeconds(1);
        scheduler.purgeExpiredRecords();
        LocalDateTime upper = LocalDateTime.now(ZoneOffset.UTC).minusDays(90).plusSeconds(1);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(analyzedLogRepository).deleteByCreatedAtBefore(captor.capture());
        assertThat(captor.getValue()).isAfterOrEqualTo(lower).isBeforeOrEqualTo(upper);
    }

    @Test
    void purge_callsAuditLogRepositoryWithCorrectCutoffDate() {
        when(analyzedLogRepository.deleteByCreatedAtBefore(any())).thenReturn(0);
        when(auditLogRepository.deleteByCreatedAtBefore(any())).thenReturn(2);

        LocalDateTime lower = LocalDateTime.now(ZoneOffset.UTC).minusDays(365).minusSeconds(1);
        scheduler.purgeExpiredRecords();
        LocalDateTime upper = LocalDateTime.now(ZoneOffset.UTC).minusDays(365).plusSeconds(1);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditLogRepository).deleteByCreatedAtBefore(captor.capture());
        assertThat(captor.getValue()).isAfterOrEqualTo(lower).isBeforeOrEqualTo(upper);
    }

    // ── Audit event creation ──────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void purge_createsAuditEventWithCorrectDeleteCounts() {
        when(analyzedLogRepository.deleteByCreatedAtBefore(any())).thenReturn(15);
        when(auditLogRepository.deleteByCreatedAtBefore(any())).thenReturn(3);

        scheduler.purgeExpiredRecords();

        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).logEvent(eq("PURGE"), eq("ANALYSIS"), isNull(), detailsCaptor.capture());
        Map<String, Object> details = detailsCaptor.getValue();
        assertThat(details).containsEntry("analyzedLogsDeleted", 15);
        assertThat(details).containsEntry("auditLogsDeleted", 3);
    }

    @Test
    void purge_skipsAuditEventWhenZeroRecordsDeleted() {
        when(analyzedLogRepository.deleteByCreatedAtBefore(any())).thenReturn(0);
        when(auditLogRepository.deleteByCreatedAtBefore(any())).thenReturn(0);

        scheduler.purgeExpiredRecords();

        verify(auditService, never()).logEvent(any(), any(), any(), any());
    }

    @Test
    void purge_createsAuditEventWhenOnlyAnalyzedLogsDeleted() {
        when(analyzedLogRepository.deleteByCreatedAtBefore(any())).thenReturn(7);
        when(auditLogRepository.deleteByCreatedAtBefore(any())).thenReturn(0);

        scheduler.purgeExpiredRecords();

        verify(auditService).logEvent(eq("PURGE"), eq("ANALYSIS"), isNull(), any());
    }

    @Test
    void purge_createsAuditEventWhenOnlyAuditLogsDeleted() {
        when(analyzedLogRepository.deleteByCreatedAtBefore(any())).thenReturn(0);
        when(auditLogRepository.deleteByCreatedAtBefore(any())).thenReturn(4);

        scheduler.purgeExpiredRecords();

        verify(auditService).logEvent(eq("PURGE"), eq("ANALYSIS"), isNull(), any());
    }

    // ── Execution order ───────────────────────────────────────────────────────

    @Test
    void purge_deletesAnalyzedLogsThenAuditLogsInOrder() {
        when(analyzedLogRepository.deleteByCreatedAtBefore(any())).thenReturn(10);
        when(auditLogRepository.deleteByCreatedAtBefore(any())).thenReturn(5);

        scheduler.purgeExpiredRecords();

        InOrder inOrder = inOrder(analyzedLogRepository, auditLogRepository);
        inOrder.verify(analyzedLogRepository).deleteByCreatedAtBefore(any());
        inOrder.verify(auditLogRepository).deleteByCreatedAtBefore(any());
    }

    // ── Exception handling ────────────────────────────────────────────────────

    @Test
    void purge_doesNotPropagateExceptions() {
        when(analyzedLogRepository.deleteByCreatedAtBefore(any()))
                .thenThrow(new RuntimeException("simulated DB failure"));

        assertThatCode(() -> scheduler.purgeExpiredRecords()).doesNotThrowAnyException();
    }
}
