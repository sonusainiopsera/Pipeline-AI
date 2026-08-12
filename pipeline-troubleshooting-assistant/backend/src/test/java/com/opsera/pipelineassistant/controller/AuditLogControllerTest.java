package com.opsera.pipelineassistant.controller;

import com.opsera.pipelineassistant.dto.Responses.AuditLogDTO;
import com.opsera.pipelineassistant.security.CustomUserDetailsService;
import com.opsera.pipelineassistant.security.JwtTokenProvider;
import com.opsera.pipelineassistant.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditLogController.class)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private AuditLogDTO sampleDTO() {
        return new AuditLogDTO(
                1L, "admin@example.com", "CREATE", "KNOWLEDGE_BASE",
                "42", null, "10.0.0.1", LocalDateTime.of(2026, 6, 15, 14, 30, 0)
        );
    }

    // ── Authorization ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldReturn200ForManagerRole() throws Exception {
        when(auditLogService.findAll(any(Pageable.class), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleDTO())));

        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void shouldReturn403ForAnalystRole() throws Exception {
        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "KB_ADMIN")
    void shouldReturn403ForKbAdminRole() throws Exception {
        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isForbidden());
    }

    // ── Response structure ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldReturnPagedAuditLogEntries() throws Exception {
        when(auditLogService.findAll(any(Pageable.class), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleDTO())));

        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(1)))
                .andExpect(jsonPath("$.content[0].actorEmail", is("admin@example.com")))
                .andExpect(jsonPath("$.content[0].action", is("CREATE")))
                .andExpect(jsonPath("$.content[0].resourceType", is("KNOWLEDGE_BASE")))
                .andExpect(jsonPath("$.content[0].resourceId", is("42")))
                .andExpect(jsonPath("$.content[0].ipAddress", is("10.0.0.1")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldReturnEmptyPageWhenNoLogs() throws Exception {
        when(auditLogService.findAll(any(Pageable.class), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    // ── Filter parameters ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldPassActionFilterToService() throws Exception {
        when(auditLogService.findAll(any(Pageable.class), eq("CREATE"), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(new PageImpl<>(List.of(sampleDTO())));

        mockMvc.perform(get("/api/audit-logs?action=CREATE"))
                .andExpect(status().isOk());

        verify(auditLogService).findAll(any(Pageable.class), eq("CREATE"), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldPassAllFiltersToService() throws Exception {
        when(auditLogService.findAll(any(Pageable.class), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/audit-logs")
                        .param("action", "UPDATE")
                        .param("resourceType", "KNOWLEDGE_BASE")
                        .param("actorEmail", "admin")
                        .param("startDate", "2026-01-01T00:00:00")
                        .param("endDate", "2026-12-31T23:59:59"))
                .andExpect(status().isOk());

        verify(auditLogService).findAll(
                any(Pageable.class),
                eq("UPDATE"),
                eq("KNOWLEDGE_BASE"),
                eq("admin"),
                eq(LocalDateTime.of(2026, 1, 1, 0, 0, 0)),
                eq(LocalDateTime.of(2026, 12, 31, 23, 59, 59))
        );
    }

    // ── Pagination defaults ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldUsePaginationDefaultsWhenNotSpecified() throws Exception {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(auditLogService.findAll(any(Pageable.class), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isOk());

        verify(auditLogService).findAll(pageableCaptor.capture(), isNull(), isNull(), isNull(), isNull(), isNull());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldCapPageSizeAt100WhenExceedsLimit() throws Exception {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(auditLogService.findAll(any(Pageable.class), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/audit-logs?size=500"))
                .andExpect(status().isOk());

        verify(auditLogService).findAll(pageableCaptor.capture(), isNull(), isNull(), isNull(), isNull(), isNull());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    // ── Date validation ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldReturn400WhenStartDateIsAfterEndDate() throws Exception {
        mockMvc.perform(get("/api/audit-logs")
                        .param("startDate", "2026-12-31T23:59:59")
                        .param("endDate", "2026-01-01T00:00:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldAllowStartDateOnlyWithoutEndDate() throws Exception {
        when(auditLogService.findAll(any(Pageable.class), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/audit-logs")
                        .param("startDate", "2026-01-01T00:00:00"))
                .andExpect(status().isOk());
    }
}
