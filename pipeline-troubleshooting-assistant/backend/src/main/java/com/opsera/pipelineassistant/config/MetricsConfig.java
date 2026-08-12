package com.opsera.pipelineassistant.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Centralises custom Micrometer metric bean definitions for the Pipeline
 * Troubleshooting Assistant.
 *
 * Metric naming follows Micrometer's lowercase dot-separated convention;
 * Prometheus scrapes automatically convert dots to underscores.
 *
 * Cardinality note: category, operation, and error_type tags are bounded to
 * the known seed values (6 categories + "unclassified", 3 operations, 3
 * error types). Do not introduce free-text tag values here.
 */
@Configuration
@RequiredArgsConstructor
public class MetricsConfig {

    private final MeterRegistry meterRegistry;

    // ── Core operational metrics ───────────────────────────────────────────────

    /**
     * Histogram timer for end-to-end log analysis latency.
     * Prometheus exposes: analysis_duration_seconds_count, _sum, _bucket.
     * Percentile histogram is enabled in application.yml:
     *   management.metrics.distribution.percentiles-histogram.analysis.duration=true
     */
    @Bean
    public Timer analysisTimer() {
        return Timer.builder("analysis.duration")
                .description("Time taken to analyze pipeline logs")
                .publishPercentileHistogram(true)
                .register(meterRegistry);
    }

    // ── Placeholder: Authentication events ────────────────────────────────────
    // TODO: Wire these counters from the Security epic (EPIC-AUTH) when JWT
    //       authentication is implemented. The counters are pre-registered here
    //       so Prometheus discovers them at startup rather than on first event.

    @Bean
    public Counter authLoginSuccessCounter() {
        return Counter.builder("auth.events")
                .description("Authentication events by type. To be wired by Security epic.")
                .tag("event_type", "login_success")
                .register(meterRegistry);
    }

    @Bean
    public Counter authLoginFailureCounter() {
        return Counter.builder("auth.events")
                .description("Authentication events by type. To be wired by Security epic.")
                .tag("event_type", "login_failure")
                .register(meterRegistry);
    }

    @Bean
    public Counter authMfaVerifyCounter() {
        return Counter.builder("auth.events")
                .description("Authentication events by type. To be wired by Security epic.")
                .tag("event_type", "mfa_verify")
                .register(meterRegistry);
    }

    @Bean
    public Counter authTokenRefreshCounter() {
        return Counter.builder("auth.events")
                .description("Authentication events by type. To be wired by Security epic.")
                .tag("event_type", "token_refresh")
                .register(meterRegistry);
    }

    // ── Placeholder: Cache operation metrics ──────────────────────────────────
    // TODO: Wire these counters from the Caching epic (WO-048 follow-up) when
    //       Caffeine cache hit/miss tracking is added to KnowledgeBaseService.
    //       The counters are pre-registered here so Prometheus discovers them
    //       at startup.

    @Bean
    public Counter cacheHitCounter() {
        return Counter.builder("cache.operations")
                .description("Knowledge base cache operation results. To be wired by Caching epic.")
                .tag("result", "hit")
                .register(meterRegistry);
    }

    @Bean
    public Counter cacheMissCounter() {
        return Counter.builder("cache.operations")
                .description("Knowledge base cache operation results. To be wired by Caching epic.")
                .tag("result", "miss")
                .register(meterRegistry);
    }
}
