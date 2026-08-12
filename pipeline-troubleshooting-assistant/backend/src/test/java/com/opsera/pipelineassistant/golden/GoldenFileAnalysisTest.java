package com.opsera.pipelineassistant.golden;

import com.opsera.pipelineassistant.model.AnalyzedLog;
import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import com.opsera.pipelineassistant.repository.AnalyzedLogRepository;
import com.opsera.pipelineassistant.repository.ErrorRepository;
import com.opsera.pipelineassistant.service.AnalysisService;
import com.opsera.pipelineassistant.service.LogSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Golden-file baseline tests for AnalysisService.analyze().
 *
 * These tests capture the exact scoring output for all 6 seed patterns defined in SeedData.java.
 * They act as behavioral contracts — any future change to the scoring constants (55, 43, 98),
 * category assignment, or response templating will cause a test here to fail, blocking the merge.
 *
 * Golden files: src/test/resources/golden/
 * Log fixtures:  src/test/resources/fixtures/
 */
@ExtendWith(MockitoExtension.class)
class GoldenFileAnalysisTest {

    @Mock
    private ErrorRepository errorRepository;

    @Mock
    private AnalyzedLogRepository analyzedLogRepository;

    @Mock
    private LogSanitizer logSanitizer;

    @InjectMocks
    private AnalysisService analysisService;

    /**
     * All 6 seed entries from SeedData.java, reproduced verbatim so the mock repository
     * returns exactly what production would return after a clean seed run.
     */
    private static final List<ErrorKnowledgeBase> SEED_ENTRIES = buildSeedEntries();

    @BeforeEach
    void setUp() {
        // Pass-through sanitizer — does not alter log text, keeping golden values stable
        lenient().when(logSanitizer.sanitize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        // Return all 6 seed entries so the service can score against the full knowledge base
        when(errorRepository.findAll()).thenReturn(SEED_ENTRIES);
        // Return the entity unchanged from save so the test can inspect computed fields
        when(analyzedLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── Parameterised golden-file comparison ──────────────────────────────────

    static Stream<Arguments> goldenTestCases() {
        return Stream.of(
            Arguments.of("oom-error",            "fixtures/oom-error-log.txt"),
            Arguments.of("jenkins-connectivity", "fixtures/jenkins-connectivity-log.txt"),
            Arguments.of("auth-failure",         "fixtures/auth-failure-log.txt"),
            Arguments.of("package-validation",   "fixtures/package-validation-log.txt"),
            Arguments.of("docker-daemon",        "fixtures/docker-daemon-log.txt"),
            Arguments.of("github-rate-limit",    "fixtures/github-rate-limit-log.txt"),
            Arguments.of("unclassified",         "fixtures/unclassified-log.txt")
        );
    }

    /**
     * Submits the fixture log text to AnalysisService.analyze() and asserts exact field-level
     * equality against the corresponding golden file.
     *
     * If any field differs, the assertion message names the pattern and field, making it
     * easy to identify which aspect of scoring behaviour changed during refactoring.
     */
    @ParameterizedTest(name = "[{index}] pattern={0}")
    @MethodSource("goldenTestCases")
    void shouldMatchGoldenFile(String patternName, String fixturePath) throws IOException {
        String logText = GoldenFileTestHelper.loadFixture(fixturePath);
        GoldenFileTestHelper.GoldenResponse expected =
            GoldenFileTestHelper.loadGolden(patternName + ".json");

        AnalyzedLog actual = analysisService.analyze(logText);

        assertThat(actual.getCategory())
            .as("category mismatch for golden pattern '%s'", patternName)
            .isEqualTo(expected.category);

        assertThat(actual.getRootCause())
            .as("rootCause mismatch for golden pattern '%s'", patternName)
            .isEqualTo(expected.rootCause);

        assertThat(actual.getSuggestedFix())
            .as("suggestedFix mismatch for golden pattern '%s'", patternName)
            .isEqualTo(expected.suggestedFix);

        assertThat(actual.getCustomerUpdate())
            .as("customerUpdate mismatch for golden pattern '%s'", patternName)
            .isEqualTo(expected.customerUpdate);

        assertThat(actual.getSeverity())
            .as("severity mismatch for golden pattern '%s'", patternName)
            .isEqualTo(expected.severity);

        assertThat(actual.getConfidence())
            .as("confidence mismatch for golden pattern '%s'", patternName)
            .isEqualTo(expected.confidence);
    }

    // ─── Seed entry builder ─────────────────────────────────────────────────────

    private static List<ErrorKnowledgeBase> buildSeedEntries() {
        return List.of(
            ErrorKnowledgeBase.builder()
                .id(1L)
                .errorPattern("OutOfMemoryError, heap space, java.lang.OutOfMemoryError")
                .category("Memory")
                .rootCause("Java heap space exhausted during pipeline execution")
                .solution("Increase JVM heap size using -Xmx flag or optimize memory usage in the build")
                .severity("HIGH")
                .build(),
            ErrorKnowledgeBase.builder()
                .id(2L)
                .errorPattern("connection refused, ECONNREFUSED, connection timeout")
                .category("Network")
                .rootCause("Service or dependency is unreachable during pipeline execution")
                .solution("Verify network connectivity and ensure all required services are running")
                .severity("HIGH")
                .build(),
            ErrorKnowledgeBase.builder()
                .id(3L)
                .errorPattern("permission denied, access denied, EACCES, unauthorized")
                .category("Permissions")
                .rootCause("Insufficient permissions to access a resource or execute an operation")
                .solution("Review and update file/directory permissions or IAM roles")
                .severity("MEDIUM")
                .build(),
            ErrorKnowledgeBase.builder()
                .id(4L)
                .errorPattern("npm ERR, node_modules, package.json, dependency resolution")
                .category("Dependency")
                .rootCause("Node.js package installation or dependency resolution failed")
                .solution("Clear npm cache and delete node_modules, then reinstall dependencies")
                .severity("MEDIUM")
                .build(),
            ErrorKnowledgeBase.builder()
                .id(5L)
                .errorPattern("docker build failed, Dockerfile, image pull, container")
                .category("Docker")
                .rootCause("Docker image build or container operation failed during pipeline")
                .solution("Review Dockerfile for errors and ensure base images are accessible")
                .severity("HIGH")
                .build(),
            ErrorKnowledgeBase.builder()
                .id(6L)
                .errorPattern("test failed, assertion error, junit, test suite")
                .category("Testing")
                .rootCause("One or more automated tests failed during the pipeline execution")
                .solution("Review test output, fix failing tests, and ensure test environment is properly configured")
                .severity("MEDIUM")
                .build()
        );
    }
}
