package com.opsera.pipelineassistant.analysis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Generates customer-ready communication text by substituting values into a
 * configurable template loaded from application.yml.
 *
 * <p>Placeholder syntax: {@code {category}}, {@code {rootCause}}, {@code {suggestedFix}}.
 * Null inputs are replaced with sensible defaults so this bean never throws for
 * valid string (or null) inputs.
 */
@Service
@Slf4j
public class ResponseTemplater {

    static final String DEFAULT_TEMPLATE =
            "We have identified the root cause of your pipeline failure as: {rootCause}. "
            + "We recommend the following action: {suggestedFix}";

    private final ResponseProperties properties;

    public ResponseTemplater(ResponseProperties properties) {
        this.properties = properties;
    }

    /**
     * Generates a formatted customer update for a matched pattern.
     *
     * @param category    detected error category (e.g. "Memory", "Network")
     * @param rootCause   root cause description
     * @param suggestedFix recommended remediation action
     * @return formatted customer communication string, never null
     */
    public String generate(String category, String rootCause, String suggestedFix) {
        String template = properties.getTemplate();
        if (template == null || template.isBlank()) {
            log.warn("analysis.response.template is not configured — falling back to hardcoded default");
            template = DEFAULT_TEMPLATE;
        }

        String safeCategory    = category    != null ? category    : "Unknown category";
        String safeRootCause   = rootCause   != null ? rootCause   : "Unknown root cause";
        String safeSuggestedFix = suggestedFix != null ? suggestedFix : "No fix available";

        return template
                .replace("{category}",    safeCategory)
                .replace("{rootCause}",   safeRootCause)
                .replace("{suggestedFix}", safeSuggestedFix);
    }

    /**
     * Returns the configured message for unclassified (no-match) analysis results.
     *
     * @return unclassified customer update string, never null
     */
    public String generateUnclassified() {
        String msg = properties.getUnclassifiedMessage();
        if (msg == null || msg.isBlank()) {
            log.warn("analysis.response.unclassified-message is not configured — falling back to hardcoded default");
            return "We are investigating the pipeline failure and will provide an update shortly.";
        }
        return msg;
    }
}
