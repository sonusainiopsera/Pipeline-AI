package com.opsera.pipelineassistant.analysis;

import org.springframework.stereotype.Service;

/**
 * Encapsulates the confidence scoring formula, reading thresholds from
 * externalized {@link ScoringProperties} rather than hardcoded constants.
 *
 * <p>Formula: {@code min(baseConfidence + (matchCount * scalingFactor / totalPatterns), maxConfidence)}
 *
 * <p>When no keywords match, or when totalPatterns is zero, the configured
 * {@code unclassifiedConfidence} value is returned instead.
 */
@Service
public class ScoringEngine {

    private final ScoringProperties properties;

    /**
     * Constructs ScoringEngine and validates that properties are internally consistent.
     *
     * @throws IllegalArgumentException if any property value violates its constraint
     */
    public ScoringEngine(ScoringProperties properties) {
        if (properties.getBaseConfidence() < 0) {
            throw new IllegalArgumentException(
                "analysis.scoring.base-confidence must be >= 0, got: " + properties.getBaseConfidence());
        }
        if (properties.getScalingFactor() <= 0) {
            throw new IllegalArgumentException(
                "analysis.scoring.scaling-factor must be > 0, got: " + properties.getScalingFactor());
        }
        if (properties.getMaxConfidence() <= properties.getBaseConfidence()) {
            throw new IllegalArgumentException(String.format(
                "analysis.scoring.max-confidence (%d) must be > base-confidence (%d)",
                properties.getMaxConfidence(), properties.getBaseConfidence()));
        }
        this.properties = properties;
    }

    /**
     * Calculates the confidence score for a pattern match.
     *
     * @param matchCount    number of keywords found in the log text (>= 0)
     * @param totalPatterns total number of keywords in the matched pattern (> 0)
     * @return confidence integer; {@code unclassifiedConfidence} when no match or zero patterns
     */
    public int calculateConfidence(int matchCount, int totalPatterns) {
        if (matchCount <= 0 || totalPatterns <= 0) {
            return properties.getUnclassifiedConfidence();
        }
        return Math.min(
            properties.getBaseConfidence() + (matchCount * properties.getScalingFactor() / totalPatterns),
            properties.getMaxConfidence()
        );
    }
}
