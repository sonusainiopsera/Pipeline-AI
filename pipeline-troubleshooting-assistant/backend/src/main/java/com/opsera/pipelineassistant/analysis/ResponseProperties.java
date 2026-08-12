package com.opsera.pipelineassistant.analysis;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for customer-facing response templates.
 * Bound from the {@code analysis.response} prefix in application.yml.
 *
 * <p>Default values reproduce the previously hardcoded strings so deployment
 * requires no configuration changes to maintain identical customer update text.
 */
@ConfigurationProperties(prefix = "analysis.response")
@Data
public class ResponseProperties {

    /**
     * Template for the customer update when a pattern is matched.
     * Supports {@code {category}}, {@code {rootCause}}, and {@code {suggestedFix}} placeholders.
     */
    private String template =
            "We have identified the root cause of your pipeline failure as: {rootCause}. "
            + "We recommend the following action: {suggestedFix}";

    /**
     * Static message returned when no pattern matches the submitted log.
     */
    private String unclassifiedMessage =
            "We are investigating the pipeline failure and will provide an update shortly.";
}
