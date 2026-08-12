package com.opsera.pipelineassistant.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for @NotBlank and @Size constraints on AnalyzeRequest and ErrorRequest.
 * Uses the Jakarta Bean Validation API directly — no Spring context required.
 *
 * AC6: verifies boundary values (exactly at limit passes, one over fails, blank fails @NotBlank).
 */
class RequestsValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    // ── AnalyzeRequest: logText @NotBlank ──────────────────────────────────────

    @Test
    void logTextBlankShouldFailNotBlank() {
        AnalyzeRequest req = new AnalyzeRequest();
        req.setLogText("");

        Set<ConstraintViolation<AnalyzeRequest>> violations = validator.validate(req);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                "logText".equals(v.getPropertyPath().toString())
                && "Log text is required".equals(v.getMessage()));
    }

    @Test
    void logTextWhitespaceShouldFailNotBlank() {
        AnalyzeRequest req = new AnalyzeRequest();
        req.setLogText("   ");

        Set<ConstraintViolation<AnalyzeRequest>> violations = validator.validate(req);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                "logText".equals(v.getPropertyPath().toString()));
    }

    @Test
    void logTextNullShouldFailNotBlank() {
        AnalyzeRequest req = new AnalyzeRequest();
        req.setLogText(null);

        Set<ConstraintViolation<AnalyzeRequest>> violations = validator.validate(req);

        assertThat(violations).isNotEmpty();
    }

    // ── AnalyzeRequest: logText @Size boundary values ─────────────────────────

    @Test
    void logTextAtExactMaxLengthShouldPassValidation() {
        AnalyzeRequest req = new AnalyzeRequest();
        req.setLogText("a".repeat(100_000));

        Set<ConstraintViolation<AnalyzeRequest>> violations = validator.validate(req);

        assertThat(violations).isEmpty();
    }

    @Test
    void logTextOneOverMaxLengthShouldFailValidation() {
        AnalyzeRequest req = new AnalyzeRequest();
        req.setLogText("a".repeat(100_001));

        Set<ConstraintViolation<AnalyzeRequest>> violations = validator.validate(req);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Log text exceeds maximum length of 100,000 characters");
    }

    @Test
    void logTextShortValueShouldPassValidation() {
        AnalyzeRequest req = new AnalyzeRequest();
        req.setLogText("Error: connection timeout");

        Set<ConstraintViolation<AnalyzeRequest>> violations = validator.validate(req);

        assertThat(violations).isEmpty();
    }

    // ── ErrorRequest: category @Size boundary values ──────────────────────────

    @Test
    void categoryAtExactMaxLengthShouldPassValidation() {
        ErrorRequest req = validErrorRequest();
        req.setCategory("a".repeat(80));

        Set<ConstraintViolation<ErrorRequest>> violations = validator.validate(req);

        assertThat(violations).isEmpty();
    }

    @Test
    void categoryOneOverMaxLengthShouldFailValidation() {
        ErrorRequest req = validErrorRequest();
        req.setCategory("a".repeat(81));

        Set<ConstraintViolation<ErrorRequest>> violations = validator.validate(req);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Category exceeds maximum length of 80 characters");
    }

    // ── ErrorRequest: errorPattern @Size boundary values ─────────────────────

    @Test
    void errorPatternAtExactMaxLengthShouldPassValidation() {
        ErrorRequest req = validErrorRequest();
        req.setErrorPattern("a".repeat(5_000));

        Set<ConstraintViolation<ErrorRequest>> violations = validator.validate(req);

        assertThat(violations).isEmpty();
    }

    @Test
    void errorPatternOneOverMaxLengthShouldFailValidation() {
        ErrorRequest req = validErrorRequest();
        req.setErrorPattern("a".repeat(5_001));

        Set<ConstraintViolation<ErrorRequest>> violations = validator.validate(req);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Error pattern exceeds maximum length of 5,000 characters");
    }

    // ── ErrorRequest: severity @Size boundary values ──────────────────────────

    @Test
    void severityAtExactMaxLengthShouldPassValidation() {
        ErrorRequest req = validErrorRequest();
        req.setSeverity("a".repeat(20));

        Set<ConstraintViolation<ErrorRequest>> violations = validator.validate(req);

        assertThat(violations).isEmpty();
    }

    @Test
    void severityOneOverMaxLengthShouldFailValidation() {
        ErrorRequest req = validErrorRequest();
        req.setSeverity("a".repeat(21));

        Set<ConstraintViolation<ErrorRequest>> violations = validator.validate(req);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Severity exceeds maximum length of 20 characters");
    }

    // ── ErrorRequest: @NotBlank on all fields ────────────────────────────────

    @Test
    void errorRequestWithAllBlankFieldsShouldHaveFiveViolations() {
        ErrorRequest req = new ErrorRequest();
        req.setErrorPattern("");
        req.setCategory("");
        req.setRootCause("");
        req.setSolution("");
        req.setSeverity("");

        Set<ConstraintViolation<ErrorRequest>> violations = validator.validate(req);

        // 5 @NotBlank violations (one per field)
        assertThat(violations).hasSize(5);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private ErrorRequest validErrorRequest() {
        ErrorRequest req = new ErrorRequest();
        req.setErrorPattern("oom,heap");
        req.setCategory("Memory");
        req.setRootCause("Java heap space exhausted");
        req.setSolution("Increase Xmx");
        req.setSeverity("HIGH");
        return req;
    }
}
