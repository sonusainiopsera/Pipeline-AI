package com.opsera.pipelineassistant.analysis;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the confidence scoring formula.
 * Bound from the {@code analysis.scoring} prefix in application.yml.
 *
 * <p>Defaults match the previously hardcoded constants so deployment requires
 * no configuration changes to maintain identical scoring behaviour.
 */
@ConfigurationProperties(prefix = "analysis.scoring")
@Data
public class ScoringProperties {

    /** Base confidence added to every matched result before scaling. Default: 55. */
    private int baseConfidence = 55;

    /** Scaling factor applied to the keyword match ratio. Default: 43. */
    private int scalingFactor = 43;

    /** Maximum confidence score that can be returned for any match. Default: 98. */
    private int maxConfidence = 98;

    /** Confidence returned when no pattern matches (unclassified result). Default: 20. */
    private int unclassifiedConfidence = 20;
}
