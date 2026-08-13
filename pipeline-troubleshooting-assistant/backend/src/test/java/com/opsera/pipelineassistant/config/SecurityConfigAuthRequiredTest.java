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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisController.class)
@ActiveProfiles("auth-required")
class SecurityConfigAuthRequiredTest {

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

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logText\":\"test log\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withoutToken_returns401JsonBody() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logText\":\"test log\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getHistory_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDashboard_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized());
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
        when(analysisService.getHistory(org.mockito.ArgumentMatchers.any())).thenReturn(
                new org.springframework.data.domain.PageImpl<>(java.util.List.of()));
        mockMvc.perform(get("/api/history")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void protectedEndpoint_withInsufficientRole_returns403JsonBody() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logText\":\"test log\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void optionsPreflightRequest_isPermittedWithoutToken() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .options("/api/analyze")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk());
    }
}
