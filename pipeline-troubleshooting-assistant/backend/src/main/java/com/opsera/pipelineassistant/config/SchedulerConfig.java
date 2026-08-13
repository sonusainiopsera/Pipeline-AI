package com.opsera.pipelineassistant.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's @Scheduled task infrastructure.
 * Placed in its own class so it can be excluded from test contexts by setting
 * app.scheduler.enabled=false in application properties.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerConfig {
}
