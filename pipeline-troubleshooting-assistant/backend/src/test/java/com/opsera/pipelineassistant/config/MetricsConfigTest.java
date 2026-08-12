package com.opsera.pipelineassistant.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MetricsConfig using SimpleMeterRegistry for fast, isolated
 * verification without Prometheus or Spring context startup.
 */
class MetricsConfigTest {

    private MeterRegistry registry;
    private MetricsConfig metricsConfig;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metricsConfig = new MetricsConfig(registry);
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

    @Test
    void analysisTimerBeanIsRegisteredWithCorrectName() {
        Timer timer = metricsConfig.analysisTimer();
        assertThat(timer).isNotNull();
        assertThat(registry.timer("analysis.duration")).isSameAs(timer);
    }

    @Test
    void analysisTimerHasDescription() {
        Timer timer = metricsConfig.analysisTimer();
        assertThat(registry.find("analysis.duration").timer()).isNotNull();
        assertThat(timer.getId().getDescription())
                .contains("analyze pipeline logs");
    }

    // ── Auth placeholder counters ──────────────────────────────────────────────

    @Test
    void authLoginSuccessCounterRegisteredWithCorrectTag() {
        Counter counter = metricsConfig.authLoginSuccessCounter();
        assertThat(counter).isNotNull();
        assertThat(counter.getId().getTag("event_type")).isEqualTo("login_success");
    }

    @Test
    void authLoginFailureCounterRegisteredWithCorrectTag() {
        Counter counter = metricsConfig.authLoginFailureCounter();
        assertThat(counter.getId().getTag("event_type")).isEqualTo("login_failure");
    }

    @Test
    void authMfaVerifyCounterRegisteredWithCorrectTag() {
        Counter counter = metricsConfig.authMfaVerifyCounter();
        assertThat(counter.getId().getTag("event_type")).isEqualTo("mfa_verify");
    }

    @Test
    void authTokenRefreshCounterRegisteredWithCorrectTag() {
        Counter counter = metricsConfig.authTokenRefreshCounter();
        assertThat(counter.getId().getTag("event_type")).isEqualTo("token_refresh");
    }

    @Test
    void allFourAuthEventCountersRegisteredWithSameName() {
        metricsConfig.authLoginSuccessCounter();
        metricsConfig.authLoginFailureCounter();
        metricsConfig.authMfaVerifyCounter();
        metricsConfig.authTokenRefreshCounter();
        assertThat(registry.find("auth.events").counters()).hasSize(4);
    }

    // ── Cache placeholder counters ─────────────────────────────────────────────

    @Test
    void cacheHitCounterRegisteredWithCorrectTag() {
        Counter counter = metricsConfig.cacheHitCounter();
        assertThat(counter.getId().getTag("result")).isEqualTo("hit");
    }

    @Test
    void cacheMissCounterRegisteredWithCorrectTag() {
        Counter counter = metricsConfig.cacheMissCounter();
        assertThat(counter.getId().getTag("result")).isEqualTo("miss");
    }

    @Test
    void bothCacheOperationCountersRegisteredWithSameName() {
        metricsConfig.cacheHitCounter();
        metricsConfig.cacheMissCounter();
        assertThat(registry.find("cache.operations").counters()).hasSize(2);
    }
}
