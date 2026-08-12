package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.dto.Responses.AuditLogDTO;
import com.opsera.pipelineassistant.model.AuditLog;
import com.opsera.pipelineassistant.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    private AuditLog buildLog(String action, String resourceType, String actorEmail) {
        return AuditLog.builder()
                .actorEmail(actorEmail)
                .action(action)
                .resourceType(resourceType)
                .resourceId("1")
                .details(Map.of("key", "value"))
                .ipAddress("10.0.0.1")
                .build();
    }

    // ── findAll — no filters ─────────────────────────────────────────────────────

    @Test
    void shouldReturnPagedResultsWhenNoFiltersApplied() {
        Pageable pageable = PageRequest.of(0, 20);
        AuditLog log = buildLog("CREATE", "KNOWLEDGE_BASE", "admin@example.com");
        when(auditLogRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(log)));

        Page<AuditLogDTO> result = auditLogService.findAll(pageable, null, null, null, null, null);

        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getContent().get(0).actorEmail()).isEqualTo("admin@example.com");
        assertThat(result.getContent().get(0).action()).isEqualTo("CREATE");
    }

    @Test
    void shouldReturnEmptyPageWhenNoAuditLogsExist() {
        Pageable pageable = PageRequest.of(0, 20);
        when(auditLogRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<AuditLogDTO> result = auditLogService.findAll(pageable, null, null, null, null, null);

        assertThat(result.getTotalElements()).isEqualTo(0L);
        assertThat(result.getContent()).isEmpty();
    }

    // ── DTO mapping ──────────────────────────────────────────────────────────────

    @Test
    void shouldMapAllEntityFieldsToDTO() {
        Pageable pageable = PageRequest.of(0, 20);
        AuditLog log = AuditLog.builder()
                .actorEmail("alice@example.com")
                .action("UPDATE")
                .resourceType("KNOWLEDGE_BASE")
                .resourceId("42")
                .details(Map.of("before", "LOW", "after", "HIGH"))
                .ipAddress("192.168.1.1")
                .build();

        when(auditLogRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(log)));

        Page<AuditLogDTO> result = auditLogService.findAll(pageable, null, null, null, null, null);

        AuditLogDTO dto = result.getContent().get(0);
        assertThat(dto.actorEmail()).isEqualTo("alice@example.com");
        assertThat(dto.action()).isEqualTo("UPDATE");
        assertThat(dto.resourceType()).isEqualTo("KNOWLEDGE_BASE");
        assertThat(dto.resourceId()).isEqualTo("42");
        assertThat(dto.details()).containsEntry("before", "LOW");
        assertThat(dto.ipAddress()).isEqualTo("192.168.1.1");
    }

    @Test
    void shouldMapNullDetailsToNullInDTO() {
        Pageable pageable = PageRequest.of(0, 20);
        AuditLog log = AuditLog.builder()
                .actorEmail("alice@example.com")
                .action("LOGIN")
                .resourceType("AUTH")
                .build();

        when(auditLogRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(log)));

        Page<AuditLogDTO> result = auditLogService.findAll(pageable, null, null, null, null, null);

        assertThat(result.getContent().get(0).details()).isNull();
    }

    // ── Filter combinations ───────────────────────────────────────────────────────

    @Test
    void shouldPassFiltersToRepositoryAndReturnFilteredResults() {
        Pageable pageable = PageRequest.of(0, 20);
        AuditLog log = buildLog("CREATE", "KNOWLEDGE_BASE", "admin@example.com");
        when(auditLogRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(log)));

        Page<AuditLogDTO> result = auditLogService.findAll(
                pageable, "CREATE", "KNOWLEDGE_BASE", "admin", null, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).action()).isEqualTo("CREATE");
        verify(auditLogRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void shouldApplyDateRangeFilterWhenBothDatesProvided() {
        Pageable pageable = PageRequest.of(0, 20);
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 12, 31, 23, 59);
        when(auditLogRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<AuditLogDTO> result = auditLogService.findAll(pageable, null, null, null, start, end);

        assertThat(result).isNotNull();
        verify(auditLogRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void shouldHandlePartialFilters() {
        Pageable pageable = PageRequest.of(0, 20);
        when(auditLogRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<AuditLogDTO> result = auditLogService.findAll(pageable, "DELETE", null, null, null, null);

        assertThat(result.getContent()).isEmpty();
        verify(auditLogRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void shouldHandleBlankStringFiltersAsNoFilter() {
        Pageable pageable = PageRequest.of(0, 20);
        AuditLog log = buildLog("LOGIN", "AUTH", "bob@example.com");
        when(auditLogRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(log)));

        Page<AuditLogDTO> result = auditLogService.findAll(pageable, "   ", "", "   ", null, null);

        assertThat(result.getContent()).hasSize(1);
    }

    // ── Pagination ────────────────────────────────────────────────────────────────

    @Test
    void shouldReturnCorrectPaginationMetadata() {
        Pageable pageable = PageRequest.of(2, 10);
        AuditLog log = buildLog("CREATE", "KNOWLEDGE_BASE", "admin@example.com");
        when(auditLogRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(log), pageable, 25L));

        Page<AuditLogDTO> result = auditLogService.findAll(pageable, null, null, null, null, null);

        assertThat(result.getNumber()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getTotalElements()).isEqualTo(25L);
        assertThat(result.getTotalPages()).isEqualTo(3);
    }
}
