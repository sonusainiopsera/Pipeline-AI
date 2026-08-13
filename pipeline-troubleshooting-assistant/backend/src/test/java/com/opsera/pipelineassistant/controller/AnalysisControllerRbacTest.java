package com.opsera.pipelineassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.pipelineassistant.dto.Responses.HistoryDetailDTO;
import com.opsera.pipelineassistant.dto.Responses.HistoryListDTO;
import com.opsera.pipelineassistant.model.AnalyzedLog;
import com.opsera.pipelineassistant.security.CustomUserDetailsService;
import com.opsera.pipelineassistant.security.JwtTokenProvider;
import com.opsera.pipelineassistant.service.AnalysisService;
import com.opsera.pipelineassistant.service.DashboardService;
import org.junit.jupiter.api.Test;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisController.class)
class AnalysisControllerRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AnalysisService analysisService;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private static final String ANALYZE_BODY = "{\"logText\":\"java.lang.OutOfMemoryError\"}";

    private void stubAnalyzeOk() {
        AnalyzedLog log = AnalyzedLog.builder()
                .id(1L).category("Memory").rootCause("Heap").suggestedFix("Xmx")
                .customerUpdate("").severity("HIGH").confidence(90).build();
        when(analysisService.analyze(anyString())).thenReturn(log);
    }

    private void stubHistoryOk() {
        when(analysisService.getHistory(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
    }

    private void stubHistoryDetailOk() {
        HistoryDetailDTO dto = new HistoryDetailDTO(1L, "log", "Memory", "Heap", "Xmx", "", "HIGH", 90, LocalDateTime.now());
        when(analysisService.historyDetail(anyLong())).thenReturn(dto);
    }

    private void stubDashboardOk() {
        when(dashboardService.getStats()).thenReturn(Map.of("totalErrors", 0, "analyzedLogs", 0, "mostCommonIssue", "None", "categoryBreakdown", Map.of()));
    }

    // ── POST /api/analyze ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ANALYST")
    void analyze_analyst_returns200() throws Exception {
        stubAnalyzeOk();
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ANALYZE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "KB_ADMIN")
    void analyze_kbAdmin_returns200() throws Exception {
        stubAnalyzeOk();
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ANALYZE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void analyze_manager_returns200() throws Exception {
        stubAnalyzeOk();
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ANALYZE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void analyze_unknownRole_returns403() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ANALYZE_BODY))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/history ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ANALYST")
    void history_analyst_returns200() throws Exception {
        stubHistoryOk();
        mockMvc.perform(get("/api/history")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "KB_ADMIN")
    void history_kbAdmin_returns200() throws Exception {
        stubHistoryOk();
        mockMvc.perform(get("/api/history")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void history_manager_returns200() throws Exception {
        stubHistoryOk();
        mockMvc.perform(get("/api/history")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void history_unknownRole_returns403() throws Exception {
        mockMvc.perform(get("/api/history")).andExpect(status().isForbidden());
    }

    // ── GET /api/history/{id} ─────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ANALYST")
    void historyDetail_analyst_returns200() throws Exception {
        stubHistoryDetailOk();
        mockMvc.perform(get("/api/history/1")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "KB_ADMIN")
    void historyDetail_kbAdmin_returns200() throws Exception {
        stubHistoryDetailOk();
        mockMvc.perform(get("/api/history/1")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void historyDetail_manager_returns200() throws Exception {
        stubHistoryDetailOk();
        mockMvc.perform(get("/api/history/1")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void historyDetail_unknownRole_returns403() throws Exception {
        mockMvc.perform(get("/api/history/1")).andExpect(status().isForbidden());
    }

    // ── GET /api/dashboard ────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ANALYST")
    void dashboard_analyst_returns200() throws Exception {
        stubDashboardOk();
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "KB_ADMIN")
    void dashboard_kbAdmin_returns200() throws Exception {
        stubDashboardOk();
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void dashboard_manager_returns200() throws Exception {
        stubDashboardOk();
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void dashboard_unknownRole_returns403() throws Exception {
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isForbidden());
    }

    // ── 403 response format ───────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "USER")
    void accessDenied_responseBodyContainsStatusAndMessage() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }
}
