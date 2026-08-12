package com.opsera.pipelineassistant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's @Scheduled task infrastructure.
 * Placed in its own class so it can be excluded from test contexts when needed.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
