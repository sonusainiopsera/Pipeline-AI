package com.opsera.pipelineassistant;

import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that /actuator/prometheus returns HTTP 200 and contains the custom
 * metric names defined in MetricsConfig and instrumented in the service layer.
 *
 * Pre-registers dynamic counters by making at least one service call before
 * asserting metric presence, since Micrometer counters only appear in the
 * Prometheus output after their first increment.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrometheusEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnalyzedLogRepository analyzedLogRepository;

    @BeforeEach
    void triggerMetricRegistrations() throws Exception {
        analyzedLogRepository.deleteAll();

        // Trigger analysis.duration timer + analysis.requests counter
        mockMvc.perform(post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"logText\":\"OutOfMemoryError heap space java.lang\"}"))
                .andExpect(status().isOk());

        // Trigger kb.operations counter (create)
        mockMvc.perform(post("/api/knowledge-base")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"errorPattern\":\"probe\",\"category\":\"Test\","
                        + "\"rootCause\":\"test\",\"solution\":\"test\",\"severity\":\"LOW\"}"))
                .andExpect(status().isCreated());

        // Trigger analysis.errors counter (validation_error)
        mockMvc.perform(post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void prometheusEndpointReturnsHttpOk() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    @Test
    void prometheusResponseContainsAnalysisDurationSecondsCount() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(content().string(containsString("analysis_duration_seconds_count")));
    }

    @Test
    void prometheusResponseContainsAnalysisDurationSecondsSum() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(content().string(containsString("analysis_duration_seconds_sum")));
    }

    @Test
    void prometheusResponseContainsAnalysisRequestsTotal() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(content().string(containsString("analysis_requests_total")));
    }

    @Test
    void prometheusResponseContainsKbOperationsTotal() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(content().string(containsString("kb_operations_total")));
    }

    @Test
    void prometheusResponseContainsAnalysisErrorsTotal() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(content().string(containsString("analysis_errors_total")));
    }

    @Test
    void prometheusResponseContainsAuthEventsPlaceholderMetric() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(content().string(containsString("auth_events")));
    }

    @Test
    void prometheusResponseContainsCacheOperationsPlaceholderMetric() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(content().string(containsString("cache_operations")));
    }
}
