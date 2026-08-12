package com.opsera.pipelineassistant.controller;

import com.opsera.pipelineassistant.dto.Responses.HistoryDetailDTO;
import com.opsera.pipelineassistant.security.CustomUserDetailsService;
import com.opsera.pipelineassistant.security.JwtTokenProvider;
import com.opsera.pipelineassistant.service.AnalysisService;
import com.opsera.pipelineassistant.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisController.class)
class AnalysisControllerDetailTest {

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

    // ── GET /api/history/{id} — 200 OK ────────────────────────────────────────

    @Test
    void shouldReturnFullDetailFor200() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2024, 3, 10, 14, 0);
        HistoryDetailDTO dto = new HistoryDetailDTO(
                1L,
                "sanitized log text",
                "Memory",
                "Heap exhausted",
                "Increase -Xmx",
                "We identified a memory issue.",
                "HIGH",
                95,
                createdAt
        );

        when(analysisService.historyDetail(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/history/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.logText", is("sanitized log text")))
                .andExpect(jsonPath("$.detectedCategory", is("Memory")))
                .andExpect(jsonPath("$.rootCause", is("Heap exhausted")))
                .andExpect(jsonPath("$.suggestedFix", is("Increase -Xmx")))
                .andExpect(jsonPath("$.customerUpdate", is("We identified a memory issue.")))
                .andExpect(jsonPath("$.severity", is("HIGH")))
                .andExpect(jsonPath("$.confidence", is(95)))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void shouldIncludeAllRequiredFieldsInDetailResponse() throws Exception {
        HistoryDetailDTO dto = new HistoryDetailDTO(
                42L, "log", "Network", "Timeout", "Check DNS", "Issue detected.", "MEDIUM", 70, null
        );

        when(analysisService.historyDetail(42L)).thenReturn(dto);

        mockMvc.perform(get("/api/history/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.logText").exists())
                .andExpect(jsonPath("$.detectedCategory").exists())
                .andExpect(jsonPath("$.rootCause").exists())
                .andExpect(jsonPath("$.suggestedFix").exists())
                .andExpect(jsonPath("$.customerUpdate").exists())
                .andExpect(jsonPath("$.severity").exists())
                .andExpect(jsonPath("$.confidence").exists());
    }

    // ── GET /api/history/{id} — 404 Not Found ────────────────────────────────

    @Test
    void shouldReturn404WithStructuredErrorWhenRecordNotFound() throws Exception {
        when(analysisService.historyDetail(999L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis record not found"));

        mockMvc.perform(get("/api/history/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", is("Analysis record not found")))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void should404NotRevealInternalState() throws Exception {
        when(analysisService.historyDetail(100L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis record not found"));

        mockMvc.perform(get("/api/history/100"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Analysis record not found")));
    }

    // ── GET /api/history/{id} — 400 Bad Request ───────────────────────────────

    @Test
    void shouldReturn400ForNonNumericId() throws Exception {
        mockMvc.perform(get("/api/history/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturn400ForZeroId() throws Exception {
        mockMvc.perform(get("/api/history/0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", containsString("Invalid analysis record ID")))
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void shouldReturn400ForNegativeId() throws Exception {
        mockMvc.perform(get("/api/history/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid analysis record ID")))
                .andExpect(jsonPath("$.status", is(400)));
    }

    // ── Content-Type verification ─────────────────────────────────────────────

    @Test
    void shouldReturnJsonContentTypeForDetailEndpoint() throws Exception {
        HistoryDetailDTO dto = new HistoryDetailDTO(
                5L, "log", "Docker", "Container OOM", "Increase limits", "Investigating.", "HIGH", 80, null
        );

        when(analysisService.historyDetail(5L)).thenReturn(dto);

        mockMvc.perform(get("/api/history/5"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
