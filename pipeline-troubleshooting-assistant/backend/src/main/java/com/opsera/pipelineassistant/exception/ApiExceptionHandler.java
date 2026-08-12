package com.opsera.pipelineassistant.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    private static final String LOG_TEXT_SIZE_MESSAGE =
            "Log text exceeds maximum length of 100,000 characters";

    /**
     * Handles @Valid/@Validated constraint failures on request body DTOs.
     *
     * Special case: a @Size violation on logText returns HTTP 413 (Payload Too Large)
     * to signal that the submitted content exceeds the application's ingest limit.
     * All other validation failures return HTTP 400 with field-level error details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest request) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();

        boolean isLogTextSizeViolation = fieldErrors.stream()
                .anyMatch(fe -> "logText".equals(fe.getField())
                        && LOG_TEXT_SIZE_MESSAGE.equals(fe.getDefaultMessage()));

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
}
