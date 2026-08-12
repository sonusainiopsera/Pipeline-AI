package com.opsera.pipelineassistant.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ScoringEngine confidence calculation.
 *
 * <p>Formula under test: min(baseConfidence + (matchCount * scalingFactor / totalPatterns), maxConfidence)
 * Default values: base=55, scaling=43, max=98, unclassified=20.
 */
class ScoringEngineTest {

    /** Creates a ScoringEngine with default configuration values. */
    private ScoringEngine engine() {
        return new ScoringEngine(new ScoringProperties());
    }

    /** Creates a ScoringEngine with custom configuration values. */
    private ScoringEngine engine(int base, int scaling, int max, int unclassified) {
        ScoringProperties props = new ScoringProperties();
        props.setBaseConfidence(base);
        props.setScalingFactor(scaling);
        props.setMaxConfidence(max);
        props.setUnclassifiedConfidence(unclassified);
        return new ScoringEngine(props);
    }

    // ── Zero / no match ────────────────────────────────────────────────────────

    @Test
    void shouldReturnUnclassifiedConfidenceWhenMatchCountIsZero() {
        // (0, 5) → no keywords matched → unclassified = 20
        assertThat(engine().calculateConfidence(0, 5)).isEqualTo(20);
    }

    @Test
    void shouldReturnUnclassifiedConfidenceWhenTotalPatternsIsZero() {
        // Division-by-zero guard: (3, 0) → unclassified = 20
        assertThat(engine().calculateConfidence(3, 0)).isEqualTo(20);
    }

    @Test
    void shouldReturnUnclassifiedConfidenceWhenBothInputsAreZero() {
        // (0, 0) → unclassified path
        assertThat(engine().calculateConfidence(0, 0)).isEqualTo(20);
    }

    @Test
    void shouldReturnUnclassifiedConfidenceWhenMatchCountIsNegative() {
        // Defensive: negative matchCount treated as no match
        assertThat(engine().calculateConfidence(-1, 5)).isEqualTo(20);
    }

    @Test
    void shouldReturnUnclassifiedConfidenceWhenTotalPatternsIsNegative() {
        // Defensive: negative totalPatterns treated as zero
        assertThat(engine().calculateConfidence(3, -1)).isEqualTo(20);
    }

    // ── Full match ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnMaxConfidenceWhenAllKeywordsMatch() {
        // (5, 5) → min(55 + 5*43/5, 98) = min(55+43, 98) = min(98, 98) = 98
        assertThat(engine().calculateConfidence(5, 5)).isEqualTo(98);
    }

    @Test
    void shouldCapAtMaxConfidenceWhenCalculationExceedsMax() {
        // (1, 1) → min(55 + 43, 98) = 98; result must not exceed 98
        int result = engine().calculateConfidence(1, 1);
        assertThat(result).isEqualTo(98);
        assertThat(result).isLessThanOrEqualTo(98);
    }

    // ── Partial match ──────────────────────────────────────────────────────────

    @Test
    void shouldCalculateProportionalScoreForPartialMatch() {
        // (3, 5) → min(55 + 3*43/5, 98) = min(55+25, 98) = 80
        // 3*43 = 129; 129/5 = 25 (integer division)
        assertThat(engine().calculateConfidence(3, 5)).isEqualTo(80);
    }

    @Test
    void shouldApplyIntegerDivisionTruncationInFormula() {
        // (1, 10) → min(55 + 1*43/10, 98) = min(55+4, 98) = 59
        // 1*43 = 43; 43/10 = 4 (integer division truncates)
        assertThat(engine().calculateConfidence(1, 10)).isEqualTo(59);
    }

    @Test
    void shouldCalculateConfidenceForOneMatchOutOfThreeKeywords() {
        // (1, 3) → 1*43/3 = 14 (integer div); confidence = 55+14 = 69
        assertThat(engine().calculateConfidence(1, 3)).isEqualTo(69);
    }

    @Test
    void shouldCalculateConfidenceForTwoMatchesOutOfFourKeywords() {
        // (2, 4) → 2*43/4 = 21 (integer div); confidence = 55+21 = 76
        assertThat(engine().calculateConfidence(2, 4)).isEqualTo(76);
    }

    // ── Custom configuration ───────────────────────────────────────────────────

    @Test
    void shouldRespectCustomBaseConfidence() {
        // base=60, scaling=40, max=100, unclassified=10
        ScoringEngine custom = engine(60, 40, 100, 10);
        // (5, 5) → min(60 + 5*40/5, 100) = min(60+40, 100) = 100
        assertThat(custom.calculateConfidence(5, 5)).isEqualTo(100);
    }

    @Test
    void shouldRespectCustomMaxConfidence() {
        // base=55, scaling=43, max=80, unclassified=20
        ScoringEngine custom = engine(55, 43, 80, 20);
        // (5, 5) → min(55+43, 80) = min(98, 80) = 80
        assertThat(custom.calculateConfidence(5, 5)).isEqualTo(80);
    }

    @Test
    void shouldRespectCustomUnclassifiedConfidence() {
        ScoringEngine custom = engine(55, 43, 98, 15);
        // Zero match → returns custom unclassified (15)
        assertThat(custom.calculateConfidence(0, 5)).isEqualTo(15);
    }

    @Test
    void shouldRespectCustomScalingFactor() {
        // base=50, scaling=20, max=100, unclassified=5
        ScoringEngine custom = engine(50, 20, 100, 5);
        // (1, 1) → min(50 + 20, 100) = 70
        assertThat(custom.calculateConfidence(1, 1)).isEqualTo(70);
    }

    // ── Constructor validation ─────────────────────────────────────────────────

    @Test
    void shouldThrowWhenBaseConfidenceIsNegative() {
        assertThatThrownBy(() -> engine(-1, 43, 98, 20))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("base-confidence");
    }

    @Test
    void shouldThrowWhenScalingFactorIsZero() {
        assertThatThrownBy(() -> engine(55, 0, 98, 20))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scaling-factor");
    }

    @Test
    void shouldThrowWhenScalingFactorIsNegative() {
        assertThatThrownBy(() -> engine(55, -1, 98, 20))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scaling-factor");
    }

    @Test
    void shouldThrowWhenMaxConfidenceEqualsBaseConfidence() {
        assertThatThrownBy(() -> engine(55, 43, 55, 20))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("max-confidence");
    }

    @Test
    void shouldThrowWhenMaxConfidenceIsLessThanBaseConfidence() {
        assertThatThrownBy(() -> engine(55, 43, 50, 20))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("max-confidence");
    }

    // ── Boundary conditions ────────────────────────────────────────────────────

    @Test
    void shouldReturnBaseConfidenceWhenMatchCountIsSmallerThanScalingGranularity() {
        // (1, 100) → 1*43/100 = 0 (integer div); confidence = 55+0 = 55
        assertThat(engine().calculateConfidence(1, 100)).isEqualTo(55);
    }

    @Test
    void shouldReturnResultBetweenUnclassifiedAndMaxForAnyValidInput() {
        ScoringEngine e = engine();
        for (int match = 1; match <= 10; match++) {
            for (int total = match; total <= 10; total++) {
                int result = e.calculateConfidence(match, total);
                assertThat(result)
                    .as("calculateConfidence(%d, %d)", match, total)
                    .isGreaterThanOrEqualTo(20)
                    .isLessThanOrEqualTo(98);
            }
        }
    }
}
