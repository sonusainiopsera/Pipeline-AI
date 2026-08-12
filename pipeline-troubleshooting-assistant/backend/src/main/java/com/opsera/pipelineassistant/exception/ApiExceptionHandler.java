package com.opsera.pipelineassistant.exception;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class ApiExceptionHandler {

    private static final String LOG_TEXT_SIZE_MESSAGE =
            "Log text exceeds maximum length of 100,000 characters";

    private final MeterRegistry meterRegistry;

    /**
     * Handles @Valid/@Validated constraint failures on request body DTOs.
     *
     * Special case: a @Size violation on logText returns HTTP 413 (Payload Too Large)
     * to signal that the submitted content exceeds the application's ingest limit.
     * All other validation failures return HTTP 400 with field-level error details.
     */
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<Map<String, Object>> handleAccountLocked(AccountLockedException ex,
                                                                    HttpServletRequest request) {
        recordErrorMetric("account_locked");
        log.warn("Login blocked — account locked: path={}", request.getRequestURI());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("message", ex.getMessage());
        body.put("status", 423);
        return ResponseEntity.status(423).body(body);
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<Map<String, Object>> handleEmailNotVerified(EmailNotVerifiedException ex,
                                                                       HttpServletRequest request) {
        recordErrorMetric("email_not_verified");
        log.warn("Login blocked — email not verified: path={}", request.getRequestURI());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("message", ex.getMessage());
        body.put("status", HttpStatus.FORBIDDEN.value());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest request) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();

        boolean isLogTextSizeViolation = fieldErrors.stream()
                .anyMatch(fe -> "logText".equals(fe.getField())
                        && LOG_TEXT_SIZE_MESSAGE.equals(fe.getDefaultMessage()));

        recordErrorMetric("validation_error");

        if (isLogTextSizeViolation) {
            log.warn("Validation failed: exceptionClass={}, status={}, path={}",
                    ex.getClass().getSimpleName(), HttpStatus.PAYLOAD_TOO_LARGE.value(), request.getRequestURI());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("timestamp", LocalDateTime.now().toString());
            body.put("message", LOG_TEXT_SIZE_MESSAGE);
            body.put("status", HttpStatus.PAYLOAD_TOO_LARGE.value());
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
        }

        log.warn("Validation failed: exceptionClass={}, status={}, path={}, fieldErrorCount={}",
                ex.getClass().getSimpleName(), HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI(), fieldErrors.size());

        List<Map<String, String>> errors = fieldErrors.stream()
                .map(fe -> {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("field", fe.getField());
                    entry.put("message", fe.getDefaultMessage());
                    return entry;
                })
                .collect(Collectors.toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("message", "Validation failed");
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                   HttpServletRequest request) {
        recordErrorMetric("validation_error");
        log.warn("Type mismatch: exceptionClass={}, paramName={}, value={}, path={}",
                ex.getClass().getSimpleName(), ex.getName(), ex.getValue(), request.getRequestURI());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("message", "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'");
        body.put("status", HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex,
                                                                      HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        String errorType = (status == HttpStatus.NOT_FOUND) ? "not_found" : "internal_error";
        recordErrorMetric(errorType);

        log.warn("Request failed: exceptionClass={}, status={}, path={}",
                ex.getClass().getSimpleName(), ex.getStatusCode().value(), request.getRequestURI());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("message", ex.getReason() != null ? ex.getReason() : ex.getMessage());
        body.put("status", ex.getStatusCode().value());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex, HttpServletRequest request) {
        recordErrorMetric("internal_error");
        log.error("Unhandled exception: exceptionClass={}, path={}",
                ex.getClass().getSimpleName(), request.getRequestURI(), ex);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("message", "An unexpected error occurred");
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private void recordErrorMetric(String errorType) {
        try {
            meterRegistry.counter("analysis.errors", "error_type", errorType).increment();
        } catch (Exception metricEx) {
            log.warn("Failed to record analysis.errors metric: {}", metricEx.getMessage());
        }
    }
}
