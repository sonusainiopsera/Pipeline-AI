package com.opsera.pipelineassistant.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ResponseTemplater placeholder substitution and null-safety.
 */
class ResponseTemplaterTest {

    /** Creates a ResponseTemplater with default configuration. */
    private ResponseTemplater templater() {
        return new ResponseTemplater(new ResponseProperties());
    }

    /** Creates a ResponseTemplater with a custom template string. */
    private ResponseTemplater templater(String template) {
        ResponseProperties props = new ResponseProperties();
        props.setTemplate(template);
        return new ResponseTemplater(props);
    }

    // ── Standard input ─────────────────────────────────────────────────────────

    @Test
    void shouldProduceExactCurrentFormatForMatchedPattern() {
        String result = templater().generate("Memory", "Java heap exhausted", "Increase -Xmx");
        assertThat(result).isEqualTo(
            "We have identified the root cause of your pipeline failure as: Java heap exhausted. "
                + "We recommend the following action: Increase -Xmx"
        );
    }

    @Test
    void shouldSubstituteAllThreePlaceholders() {
        ResponseProperties props = new ResponseProperties();
        props.setTemplate("Category: {category} | Root: {rootCause} | Fix: {suggestedFix}");
        String result = new ResponseTemplater(props).generate("Docker", "Build failed", "Check Dockerfile");
        assertThat(result).isEqualTo("Category: Docker | Root: Build failed | Fix: Check Dockerfile");
    }

    @Test
    void shouldReturnUnclassifiedMessageFromDefaultConfig() {
        assertThat(templater().generateUnclassified())
            .isEqualTo("We are investigating the pipeline failure and will provide an update shortly.");
    }

    // ── Null / empty inputs ────────────────────────────────────────────────────

    @Test
    void shouldReplaceNullCategoryWithDefault() {
        ResponseProperties props = new ResponseProperties();
        props.setTemplate("{category}");
        String result = new ResponseTemplater(props).generate(null, "root", "fix");
        assertThat(result).isEqualTo("Unknown category");
    }

    @Test
    void shouldReplaceNullRootCauseWithDefault() {
        ResponseProperties props = new ResponseProperties();
        props.setTemplate("{rootCause}");
        String result = new ResponseTemplater(props).generate("Memory", null, "fix");
        assertThat(result).isEqualTo("Unknown root cause");
    }

    @Test
    void shouldReplaceNullSuggestedFixWithDefault() {
        ResponseProperties props = new ResponseProperties();
        props.setTemplate("{suggestedFix}");
        String result = new ResponseTemplater(props).generate("Memory", "root", null);
        assertThat(result).isEqualTo("No fix available");
    }

    @Test
    void shouldHandleAllNullInputsWithoutThrowing() {
        String result = templater().generate(null, null, null);
        assertThat(result).isNotNull()
            .contains("Unknown root cause")
            .contains("No fix available");
    }

    // ── Blank / missing template falls back to hardcoded default ──────────────

    @Test
    void shouldFallBackToHardcodedDefaultWhenTemplateIsNull() {
        ResponseProperties props = new ResponseProperties();
        props.setTemplate(null);
        String result = new ResponseTemplater(props).generate("Memory", "Heap exhausted", "Increase -Xmx");
        assertThat(result).isEqualTo(
            "We have identified the root cause of your pipeline failure as: Heap exhausted. "
                + "We recommend the following action: Increase -Xmx"
        );
    }

    @Test
    void shouldFallBackToHardcodedDefaultWhenTemplateIsBlank() {
        ResponseProperties props = new ResponseProperties();
        props.setTemplate("   ");
        String result = new ResponseTemplater(props).generate("Memory", "Heap exhausted", "Increase -Xmx");
        assertThat(result).isEqualTo(
            "We have identified the root cause of your pipeline failure as: Heap exhausted. "
                + "We recommend the following action: Increase -Xmx"
        );
    }

    @Test
    void shouldFallBackForUnclassifiedMessageWhenNull() {
        ResponseProperties props = new ResponseProperties();
        props.setUnclassifiedMessage(null);
        String result = new ResponseTemplater(props).generateUnclassified();
        assertThat(result).isEqualTo(
            "We are investigating the pipeline failure and will provide an update shortly."
        );
    }

    // ── Custom template configuration ──────────────────────────────────────────

    @Test
    void shouldRespectCustomTemplate() {
        String result = templater("[{category}] {rootCause} — Action: {suggestedFix}")
            .generate("Network", "Connection refused", "Check firewall");
        assertThat(result).isEqualTo("[Network] Connection refused — Action: Check firewall");
    }

    @Test
    void shouldRespectCustomUnclassifiedMessage() {
        ResponseProperties props = new ResponseProperties();
        props.setUnclassifiedMessage("Custom unclassified response.");
        assertThat(new ResponseTemplater(props).generateUnclassified())
            .isEqualTo("Custom unclassified response.");
    }

    // ── Preserves special characters ───────────────────────────────────────────

    @Test
    void shouldPreserveSpecialCharactersInInputs() {
        String rootCause = "Error: java.lang.NullPointerException at line 42 (newline\nhere)";
        String result = templater().generate("Java", rootCause, "fix");
        assertThat(result).contains(rootCause);
    }

    @Test
    void shouldHandleLongInputsWithoutTruncation() {
        String longString = "x".repeat(10_000);
        String result = templater().generate("Cat", longString, "fix");
        assertThat(result).contains(longString);
    }

    // ── Output is never null ───────────────────────────────────────────────────

    @Test
    void generateNeverReturnsNull() {
        assertThat(templater().generate("a", "b", "c")).isNotNull();
        assertThat(templater().generate(null, null, null)).isNotNull();
    }

    @Test
    void generateUnclassifiedNeverReturnsNull() {
        assertThat(templater().generateUnclassified()).isNotNull();
    }
}
