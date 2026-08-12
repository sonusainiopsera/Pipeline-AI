package com.opsera.pipelineassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.pipelineassistant.model.AnalyzedLog;
import com.opsera.pipelineassistant.service.AnalysisService;
import com.opsera.pipelineassistant.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisController.class)
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AnalysisService analysisService;

    @MockBean
    private DashboardService dashboardService;

    // ─── POST /api/analyze ──────────────────────────────────────────────────────

    @Test
    void shouldAnalyzeLogSuccessfully() throws Exception {
        AnalyzedLog response = AnalyzedLog.builder()
                .id(1L)
                .logText("java.lang.OutOfMemoryError: Java heap space")
                .category("Memory")
                .rootCause("Java heap space exhausted during pipeline execution")
                .suggestedFix("Increase JVM heap size using -Xmx flag or optimize memory usage in the build")
                .customerUpdate("We have identified the root cause of your pipeline failure as: Java heap space exhausted.")
                .severity("HIGH")
                .confidence(98)
                .build();

        when(analysisService.analyze(anyString())).thenReturn(response);

        String requestBody = objectMapper.writeValueAsString(
                Map.of("logText", "java.lang.OutOfMemoryError: Java heap space"));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.category", is("Memory")))
                .andExpect(jsonPath("$.rootCause", is("Java heap space exhausted during pipeline execution")))
                .andExpect(jsonPath("$.suggestedFix", is("Increase JVM heap size using -Xmx flag or optimize memory usage in the build")))
                .andExpect(jsonPath("$.severity", is("HIGH")))
                .andExpect(jsonPath("$.confidence", is(98)));
    }

    @Test
    void shouldReturn400WhenLogTextIsBlank() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of("logText", ""));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenLogTextIsMissing() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenLogTextIsWhitespaceOnly() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of("logText", "   "));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn415WhenContentTypeIsNotJson() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("some log text"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void shouldReturn400WhenBodyIsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{malformed json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn413WhenLogTextExceedsMaxLength() throws Exception {
        String oversizedLogText = "a".repeat(100_001);
        String requestBody = objectMapper.writeValueAsString(Map.of("logText", oversizedLogText));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.message")
                        .value("Log text exceeds maximum length of 100,000 characters"));
    }

    @Test
    void shouldReturn400WithFieldErrorWhenLogTextIsBlankStructuredResponse() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of("logText", ""));

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("logText"));
    }

    // ─── GET /api/history ──────────────────────────────────────────────────────

    @Test
    void shouldReturnHistoryList() throws Exception {
        AnalyzedLog log1 = AnalyzedLog.builder()
                .id(1L)
                .category("Memory")
                .severity("HIGH")
                .confidence(98)
                .build();
        AnalyzedLog log2 = AnalyzedLog.builder()
                .id(2L)
                .category("Network")
                .severity("HIGH")
                .confidence(85)
                .build();

        when(analysisService.getHistory()).thenReturn(List.of(log1, log2));

        mockMvc.perform(get("/api/history"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].category", is("Memory")))
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].category", is("Network")));
    }

    @Test
    void shouldReturnEmptyHistoryList() throws Exception {
        when(analysisService.getHistory()).thenReturn(List.of());

        mockMvc.perform(get("/api/history"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ─── GET /api/dashboard ────────────────────────────────────────────────────

    @Test
    void shouldReturnDashboardStats() throws Exception {
        Map<String, Object> stats = Map.of(
                "totalErrors", 6,
                "analyzedLogs", 42,
                "mostCommonIssue", "Memory",
                "categoryBreakdown", Map.of("Memory", 10, "Network", 8)
        );

        when(dashboardService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalErrors", is(6)))
                .andExpect(jsonPath("$.analyzedLogs", is(42)))
                .andExpect(jsonPath("$.mostCommonIssue", is("Memory")))
                .andExpect(jsonPath("$.categoryBreakdown").exists());
    }

    @Test
    void shouldReturnDashboardStatsWithZeroCounts() throws Exception {
        Map<String, Object> stats = Map.of(
                "totalErrors", 0,
                "analyzedLogs", 0,
                "mostCommonIssue", "None",
                "categoryBreakdown", Map.of()
        );

        when(dashboardService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalErrors", is(0)))
                .andExpect(jsonPath("$.analyzedLogs", is(0)));
    }

    @Test
    void shouldReturnJsonContentTypeForHistoryAndDashboard() throws Exception {
        when(analysisService.getHistory()).thenReturn(List.of());
        when(dashboardService.getStats()).thenReturn(Map.of(
                "totalErrors", 0,
                "analyzedLogs", 0,
                "mostCommonIssue", "None",
                "categoryBreakdown", Map.of()
        ));

        mockMvc.perform(get("/api/history"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
