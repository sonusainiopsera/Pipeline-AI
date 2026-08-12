package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import com.opsera.pipelineassistant.repository.ErrorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end cache integration test: verifies that repeated HTTP analysis requests
 * hit the Caffeine cache instead of querying the database again, and that creating
 * a KB entry evicts the cache causing the next request to reload from the database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KnowledgeBaseCacheIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private AnalyzedLogRepository analyzedLogRepository;

    @SpyBean
    private ErrorRepository errorRepository;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("knowledgeBase").clear();
        analyzedLogRepository.deleteAll();
    }

    @Test
    void secondAnalysisRequestDoesNotQueryDatabase() throws Exception {
        String body = "{\"logText\":\"OutOfMemoryError heap space java.lang.OutOfMemoryError\"}";

        mockMvc.perform(post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());

        // Both requests go through KnowledgeBaseService.getAllEntries(), but the cache
        // serves the second call — only one real findAll() should have reached the repository
        verify(errorRepository, times(1)).findAll();
    }

    @Test
    void createKbEntryEvictsCacheSoNextAnalysisQueriesDatabase() throws Exception {
        String analyzeBody = "{\"logText\":\"OutOfMemoryError heap space java.lang.OutOfMemoryError\"}";

        // First analysis loads the cache
        mockMvc.perform(post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content(analyzeBody))
                .andExpect(status().isOk());

        // Create a new KB entry — @CacheEvict fires, cache is cleared
        String kbBody = "{\"errorPattern\":\"new pattern\",\"category\":\"Test\","
                + "\"rootCause\":\"Test cause\",\"solution\":\"Test fix\",\"severity\":\"LOW\"}";
        mockMvc.perform(post("/api/knowledge-base")
                .contentType(MediaType.APPLICATION_JSON)
                .content(kbBody))
                .andExpect(status().isOk());

        // Next analysis should re-query the database because the cache was evicted
        mockMvc.perform(post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content(analyzeBody))
                .andExpect(status().isOk());

        // findAll() called once before create + once after eviction = 2 total
        verify(errorRepository, times(2)).findAll();
    }
}
