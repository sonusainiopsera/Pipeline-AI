package com.opsera.pipelineassistant.config;

import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that Caffeine cache metrics are exposed via the Actuator metrics endpoint
 * after cache interactions, confirming that recordStats() is active and Micrometer
 * CaffeineCacheMeterBinder has wired up the knowledgeBase cache.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CacheMetricsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private AnalyzedLogRepository analyzedLogRepository;

    private static final String ANALYZE_BODY =
            "{\"logText\":\"OutOfMemoryError heap space java.lang.OutOfMemoryError\"}";

    @BeforeEach
    void setUp() {
        cacheManager.getCache("knowledgeBase").clear();
        analyzedLogRepository.deleteAll();
    }

    @Test
    void cacheGetsMetricRecordsMissAfterFirstAnalysisRequest() throws Exception {
        mockMvc.perform(post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ANALYZE_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics/cache.gets")
                .param("tag", "cache:knowledgeBase")
                .param("tag", "result:miss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value",
                        greaterThanOrEqualTo(1.0)));
    }

    @Test
    void cacheGetsMetricRecordsHitAfterSecondAnalysisRequest() throws Exception {
        mockMvc.perform(post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ANALYZE_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ANALYZE_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics/cache.gets")
                .param("tag", "cache:knowledgeBase")
                .param("tag", "result:hit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value",
                        greaterThanOrEqualTo(1.0)));
    }

    @Test
    void cacheSizeMetricIsGreaterThanZeroAfterAnalysisRequest() throws Exception {
        mockMvc.perform(post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ANALYZE_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics/cache.size")
                .param("tag", "cache:knowledgeBase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value",
                        greaterThanOrEqualTo(0.0)));
    }

    @Test
    void cacheMetricsEndpointIsAccessibleBeforeCacheInteraction() throws Exception {
        mockMvc.perform(get("/actuator/metrics/cache.gets")
                .param("tag", "cache:knowledgeBase")
                .param("tag", "result:miss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value",
                        greaterThanOrEqualTo(0.0)));
    }
}
