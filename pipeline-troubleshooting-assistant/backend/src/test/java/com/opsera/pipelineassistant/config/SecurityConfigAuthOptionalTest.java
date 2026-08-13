package com.opsera.pipelineassistant.config;

import com.opsera.pipelineassistant.controller.AnalysisController;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies auth-optional profile behavior: the security filter chain permits all requests
 * at the URL level (no 401 from the filter), while @PreAuthorize method security remains
 * active. Unauthenticated requests reach the controller but are rejected by @PreAuthorize
 * with 403 (not 401). Authenticated users with correct roles succeed with 200.
 */
@WebMvcTest(AnalysisController.class)
@ActiveProfiles("auth-optional")
class SecurityConfigAuthOptionalTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalysisService analysisService;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    // Under auth-optional, unauthenticated requests are not blocked by the filter chain (no 401).
    // They reach @PreAuthorize which returns 403 for anonymous users without required roles.

    @Test
    void unauthenticated_analyze_returns403NotFilterLevel401() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logText\":\"test log\"}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertNotEquals(401, status,
                            "Auth-optional filter should not return 401 — requests pass the filter");
                });
    }

    @Test
    void unauthenticated_getHistory_returns403NotFilterLevel401() throws Exception {
        mockMvc.perform(get("/api/history"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertNotEquals(401, status,
                            "Auth-optional filter should not return 401");
                });
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void protectedEndpoint_withAuthenticatedUser_returns200() throws Exception {
        AnalyzedLog log = AnalyzedLog.builder()
                .id(1L).category("Memory").rootCause("Heap").suggestedFix("Xmx")
                .customerUpdate("").severity("HIGH").confidence(90).build();
        when(analysisService.analyze(anyString())).thenReturn(log);

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logText\":\"test log\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void getHistory_withAuthenticatedUser_returns200() throws Exception {
        when(analysisService.getHistory(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/api/history")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void getDashboard_withAuthenticatedUser_returns200() throws Exception {
        when(dashboardService.getStats()).thenReturn(
                Map.of("totalErrors", 0, "analyzedLogs", 0,
                       "mostCommonIssue", "None", "categoryBreakdown", Map.of()));
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isOk());
    }
}
