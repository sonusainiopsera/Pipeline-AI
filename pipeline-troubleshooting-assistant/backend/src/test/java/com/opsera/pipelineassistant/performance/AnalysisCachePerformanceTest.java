package com.opsera.pipelineassistant.performance;

import com.opsera.pipelineassistant.repository.ErrorRepository;
import com.opsera.pipelineassistant.service.AnalysisService;
import com.opsera.pipelineassistant.service.KnowledgeBaseService;
import com.opsera.pipelineassistant.testutil.KnowledgeBaseTestDataFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance test suite validating that the analysis endpoint meets its p95 latency SLOs
 * under both warm-cache and cold-cache conditions with a 500-entry knowledge base.
 *
 * PRD Performance Requirements:
 *   - Warm cache (Caffeine hit): p95 response time < 50ms
 *   - Cold cache (DB query):     p95 response time < 200ms
 *
 * Run with: mvn verify -Pperformance
 * Excluded from the default 'mvn test' goal via the @Tag("performance") exclusion in Surefire.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class AnalysisCachePerformanceTest {

    // ── SLO thresholds (PRD Performance Requirements) ────────────────────────

    /** Maximum p95 analysis latency with a warm Caffeine cache, in milliseconds. */
    static final long WARM_CACHE_P95_MS = 50;

    /** Maximum p95 analysis latency with a cold cache (DB query path), in milliseconds. */
    static final long COLD_CACHE_P95_MS = 200;

    // ── Testcontainers PostgreSQL ─────────────────────────────────────────────

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("pipelinedb_perf")
                    .withUsername("perftest")
                    .withPassword("perftest");

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    // ── Spring-managed dependencies ────────────────────────────────────────────

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private ErrorRepository errorRepository;

    @Autowired
    private CacheManager cacheManager;

    // ── Sample log text (~2500 characters, contains keywords from multiple categories) ──

    private static final String SAMPLE_LOG =
            "[2024-01-15 10:23:45] ERROR authentication failure detected\n" +
            "login failed: invalid credentials provided for user alice@company.com\n" +
            "password incorrect after 3 attempts - authentication error occurred\n" +
            "token expired - session timeout forced logout, unauthorized access blocked\n\n" +
            "[2024-01-15 10:23:46] ERROR Docker container startup failed\n" +
            "container failed to initialize - image not found: myapp:2.0.1 in registry\n" +
            "volume mount error: permission denied on /data/config, port conflict on 8080\n" +
            "docker daemon not responding - container exit code 137 (OOM)\n\n" +
            "[2024-01-15 10:23:47] FATAL java.lang.OutOfMemoryError: Java heap space\n" +
            "GC overhead limit exceeded after extended garbage collection cycles\n" +
            "heap space exhausted: 512MB limit reached, OOM killer triggered by kernel\n" +
            "memory leak detected in connection pool, swap exhausted on host\n\n" +
            "[2024-01-15 10:23:48] ERROR Network connectivity failure\n" +
            "connection refused to https://api.service.internal:443 after 30s\n" +
            "DNS resolution failed for hostname: internal.service.local\n" +
            "SSL certificate expired - TLS handshake failed, network unreachable\n" +
            "request timeout: packet loss detected, firewall blocked outbound traffic\n\n" +
            "[2024-01-15 10:23:49] ERROR Build process terminated with exit code 1\n" +
            "Maven compile error: dependency missing - artifact resolution failed\n" +
            "build failed due to classpath conflict between commons-lang:2.6 and 3.12\n" +
            "JAR not found in local repository: com.example:missing-lib:1.0.0\n\n" +
            "[2024-01-15 10:23:50] ERROR Kubernetes deployment rollout failed\n" +
            "pod crash in namespace production after 3 consecutive restarts\n" +
            "health check failed: readiness probe timeout after 30s\n" +
            "liveness probe failed - restart loop (CrashLoopBackOff) detected\n" +
            "deployment timeout: rollout did not complete within 600s\n\n" +
            "[2024-01-15 10:23:51] ERROR Database connectivity issue\n" +
            "connection pool exhausted: all 50 connections in use\n" +
            "query timeout after 30000ms - deadlock detected between transactions\n" +
            "schema migration failed: constraint violation on table analyzed_logs\n" +
            "connection refused: PostgreSQL at 10.0.0.5:5432 unreachable\n\n" +
            "Stack trace:\n" +
            "  at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1060)\n" +
            "  at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014)\n" +
            "  at javax.servlet.http.HttpServlet.service(HttpServlet.java:497)\n" +
            "  at org.springframework.security.web.FilterChainProxy.doFilter(FilterChainProxy.java:204)\n" +
            "  ... 38 more frames omitted\n\n" +
            "System info: JVM=21.0.1, OS=Linux 5.15, Memory=512MB heap/2GB total\n" +
            "Application: pipeline-assistant v1.2.3, Instance: pod/myapp-abc123\n" +
            "Request-ID: 550e8400-e29b-41d4-a716-446655440000\n" +
            "Duration: 28547ms, Namespace: production, Node: gke-node-abc\n";

    // ── Test lifecycle ─────────────────────────────────────────────────────────

    @BeforeAll
    void seedKnowledgeBase() {
        errorRepository.saveAll(KnowledgeBaseTestDataFactory.generateEntries(500));
        long totalEntries = errorRepository.count();
        log.info("Performance test seeding complete — {} total KB entries in database", totalEntries);
    }

    // ── Test scenarios ─────────────────────────────────────────────────────────

    /**
     * Cold-cache scenario: clears the Caffeine cache, then performs 20 sequential analysis
     * requests. The first request fetches all KB entries from PostgreSQL; subsequent requests
     * benefit from the warmed cache. P95 of all 20 requests must be < 200ms.
     *
     * Design note: P95 of 20 requests (sorted[19]) captures the cold DB-fetch latency.
     * The SLO of 200ms covers the worst-case cold start.
     */
    @Test
    void coldCacheP95LatencyMeetsSLO() {
        // JVM and connection-pool warm-up: 5 requests (cache loaded, then discarded)
        for (int i = 0; i < 5; i++) {
            analysisService.analyze(SAMPLE_LOG);
        }

        // Clear cache to force a cold DB query on the first measured request
        Objects.requireNonNull(cacheManager.getCache("knowledgeBase")).clear();

        // Measure 20 sequential requests
        List<Long> latenciesNs = new ArrayList<>(20);
        for (int i = 0; i < 20; i++) {
            latenciesNs.add(measureAnalysisNanos());
        }
        Collections.sort(latenciesNs);

        long p50Ms  = percentileMs(latenciesNs, 0.50);
        long p95Ms  = percentileMs(latenciesNs, 0.95);
        long p99Ms  = percentileMs(latenciesNs, 0.99);
        log.info("Cold-cache latency: p50={}ms  p95={}ms  p99={}ms  (SLO: p95 < {}ms)",
                p50Ms, p95Ms, p99Ms, COLD_CACHE_P95_MS);

        assertThat(p95Ms)
                .as("Cold-cache p95 latency %dms must be < %dms SLO", p95Ms, COLD_CACHE_P95_MS)
                .isLessThan(COLD_CACHE_P95_MS);
    }

    /**
     * Warm-cache scenario: primes the Caffeine cache with one request, then performs
     * 105 sequential analysis requests (first 5 discarded for JVM warm-up), and asserts
     * the p95 of the remaining 100 is < 50ms.
     */
    @Test
    void warmCacheP95LatencyMeetsSLO() {
        // Prime the cache
        analysisService.analyze(SAMPLE_LOG);

        // Discard first 5 requests as JVM warm-up
        for (int i = 0; i < 5; i++) {
            analysisService.analyze(SAMPLE_LOG);
        }

        // Measure 100 warm-cache requests
        List<Long> latenciesNs = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            latenciesNs.add(measureAnalysisNanos());
        }
        Collections.sort(latenciesNs);

        long p50Ms  = percentileMs(latenciesNs, 0.50);
        long p95Ms  = percentileMs(latenciesNs, 0.95);
        long p99Ms  = percentileMs(latenciesNs, 0.99);
        log.info("Warm-cache latency:  p50={}ms  p95={}ms  p99={}ms  (SLO: p95 < {}ms)",
                p50Ms, p95Ms, p99Ms, WARM_CACHE_P95_MS);

        assertThat(p95Ms)
                .as("Warm-cache p95 latency %dms must be < %dms SLO", p95Ms, WARM_CACHE_P95_MS)
                .isLessThan(WARM_CACHE_P95_MS);
    }

    /**
     * Post-eviction latency scenario: warms the cache, triggers a cache eviction via
     * KnowledgeBaseService.create(), and asserts that the immediately following analysis
     * request (cold cache after eviction) completes within the cold-cache SLO of 200ms.
     */
    @Test
    void postEvictionLatencyMeetsColdCacheSLO() {
        // Warm the cache
        analysisService.analyze(SAMPLE_LOG);

        // Trigger cache eviction via a new KB entry creation
        var newEntry = com.opsera.pipelineassistant.model.ErrorKnowledgeBase.builder()
                .errorPattern("eviction-test-pattern, post-eviction-keyword")
                .category("TestEviction")
                .rootCause("Post-eviction latency test entry")
                .solution("No action required — test entry only")
                .severity("LOW")
                .build();
        knowledgeBaseService.create(newEntry);

        // Measure the first request after eviction (must re-fetch from DB)
        long latencyMs = measureAnalysisNanos() / 1_000_000;
        log.info("Post-eviction latency: {}ms  (SLO: < {}ms)", latencyMs, COLD_CACHE_P95_MS);

        assertThat(latencyMs)
                .as("Post-eviction latency %dms must be < %dms cold-cache SLO", latencyMs, COLD_CACHE_P95_MS)
                .isLessThan(COLD_CACHE_P95_MS);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long measureAnalysisNanos() {
        long start = System.nanoTime();
        analysisService.analyze(SAMPLE_LOG);
        return System.nanoTime() - start;
    }

    /**
     * Returns the p-th percentile from a sorted list of nanosecond latencies, converted to ms.
     * Uses floor(p * count) as the index, capped at count-1 to prevent out-of-bounds.
     */
    private long percentileMs(List<Long> sortedNanos, double p) {
        int idx = Math.min((int) Math.floor(p * sortedNanos.size()), sortedNanos.size() - 1);
        return sortedNanos.get(idx) / 1_000_000;
    }
}
