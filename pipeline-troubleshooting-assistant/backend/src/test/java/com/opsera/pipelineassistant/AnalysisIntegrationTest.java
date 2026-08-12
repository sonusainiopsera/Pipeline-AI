package com.opsera.pipelineassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.pipelineassistant.model.AnalyzedLog;
import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AnalysisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnalyzedLogRepository analyzedLogRepository;

    @BeforeEach
    void cleanup() {
        analyzedLogRepository.deleteAll();
    }

    @Test
    void shouldSanitizeSecretsBeforePersistingToDatabase() throws Exception {
        String logWithSecrets =
            "Pipeline failed: AKIAIOSFODNN7EXAMPLE access denied, "
            + "Bearer eyJhbGciOiJIUzI1NiJ9.test returned 401, "
            + "password=SuperSecret123 rejected";

        mockMvc.perform(post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("logText", logWithSecrets))))
                .andExpect(status().isOk());

        List<AnalyzedLog> saved = analyzedLogRepository.findAll();
        assertThat(saved).hasSize(1);
        String savedLogText = saved.get(0).getLogText();

        assertThat(savedLogText).doesNotContain("AKIAIOSFODNN7EXAMPLE");
        assertThat(savedLogText).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
        assertThat(savedLogText).doesNotContain("SuperSecret123");
        assertThat(savedLogText).contains("[AWS_KEY_REDACTED]");
        assertThat(savedLogText).contains("[BEARER_TOKEN_REDACTED]");
        assertThat(savedLogText).contains("[PASSWORD_REDACTED]");
    }

    @Test
    void shouldReturnOnlySanitizedTextFromHistoryEndpoint() throws Exception {
        String logWithSecrets =
            "AKIAIOSFODNN7EXAMPLE and Bearer eyJhbGciOiJIUzI1NiJ9.test and password=SuperSecret123";

        mockMvc.perform(post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("logText", logWithSecrets))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].logText", not(containsString("AKIAIOSFODNN7EXAMPLE"))))
                .andExpect(jsonPath("$[0].logText", not(containsString("SuperSecret123"))))
                .andExpect(jsonPath("$[0].logText", containsString("[AWS_KEY_REDACTED]")))
                .andExpect(jsonPath("$[0].logText", containsString("[PASSWORD_REDACTED]")));
    }
}
