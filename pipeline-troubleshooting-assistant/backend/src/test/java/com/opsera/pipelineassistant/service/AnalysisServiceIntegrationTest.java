package com.opsera.pipelineassistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the full analyze pipeline: HTTP request → AnalysisService →
 * PatternMatcher + ScoringEngine + ResponseTemplater → persisted AnalyzedLog response.
 *
 * Verifies that POST /api/analyze produces correct category and confidence for all 6
 * seed patterns and for an unclassified input, confirming the orchestration pipeline
 * works end-to-end through the web layer.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AnalysisServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String loadFixture(String path) throws IOException {
        return new ClassPathResource(path)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    // ── Parameterised test across all 6 seed patterns ─────────────────────────

    static Stream<Arguments> seedPatternGolden() {
        return Stream.of(
            Arguments.of("fixtures/oom-error-log.txt",            "Memory",      98),
            Arguments.of("fixtures/jenkins-connectivity-log.txt",  "Network",     98),
            Arguments.of("fixtures/auth-failure-log.txt",          "Permissions", 98),
            Arguments.of("fixtures/package-validation-log.txt",    "Dependency",  98),
            Arguments.of("fixtures/docker-daemon-log.txt",         "Docker",      98),
            Arguments.of("fixtures/github-rate-limit-log.txt",     "Testing",     98)
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("seedPatternGolden")
    void shouldReturnCorrectCategoryAndConfidenceForSeedPattern(
            String fixturePath, String expectedCategory, int expectedConfidence) throws Exception {

        String logText = loadFixture(fixturePath);

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("logText", logText))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value(expectedCategory))
                .andExpect(jsonPath("$.confidence").value(expectedConfidence));
    }

    // ── Unclassified path ─────────────────────────────────────────────────────

    @Test
    void shouldReturnUnclassifiedCategoryAndConfidence20ForUnknownLog() throws Exception {
        String logText = loadFixture("fixtures/unclassified-log.txt");

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("logText", logText))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("Unclassified"))
                .andExpect(jsonPath("$.confidence").value(20))
                .andExpect(jsonPath("$.rootCause").value("Unable to determine root cause from the provided log."))
                .andExpect(jsonPath("$.suggestedFix").value("Please review the log manually or contact support."));
    }

    // ── customerUpdate content spot-check ────────────────────────────────────

    @Test
    void shouldIncludeRootCauseInCustomerUpdateForMatchedPattern() throws Exception {
        String logText = loadFixture("fixtures/oom-error-log.txt");

        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("logText", logText))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerUpdate")
                        .value("We have identified the root cause of your pipeline failure as: "
                                + "Java heap space exhausted during pipeline execution. "
                                + "We recommend the following action: "
                                + "Increase JVM heap size using -Xmx flag or optimize memory usage in the build"));
    }
}
