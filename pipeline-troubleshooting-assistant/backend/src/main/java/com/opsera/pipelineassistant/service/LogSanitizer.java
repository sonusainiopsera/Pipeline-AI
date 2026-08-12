package com.opsera.pipelineassistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Redacts secrets and PII from raw log text before persistence or display.
 * All patterns are compiled once at class-load time (static final) for performance.
 */
@Service
public class LogSanitizer {

    private static final Logger log = LoggerFactory.getLogger(LogSanitizer.class);

    private record SanitizationRule(Pattern pattern, String replacement) {}

    private static final List<SanitizationRule> RULES = List.of(

        // AWS IAM access keys — format: AKIA + 16 uppercase letters/digits
        new SanitizationRule(
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            "[AWS_KEY_REDACTED]"
        ),

        // HTTP Bearer tokens (OAuth2, JWT)
        new SanitizationRule(
            Pattern.compile("Bearer\\s+[A-Za-z0-9\\-._~+/]+=*"),
            "[BEARER_TOKEN_REDACTED]"
        ),

        // Password / passwd / pwd / secret assignments (key=value or key:value)
        // $1 preserves the keyword name; replacement normalises separator to '='
        new SanitizationRule(
            Pattern.compile("(?i)(password|passwd|pwd|secret)[=:]\\S+"),
            "$1=[PASSWORD_REDACTED]"
        ),

        // Generic API key assignments (api_key, api-key, apikey)
        new SanitizationRule(
            Pattern.compile("(?i)(api[_-]?key|apikey)\\s*[=:]\\s*\\S+"),
            "$1=[API_KEY_REDACTED]"
        ),

        // PEM private key blocks (RSA, EC, OPENSSH, etc.)
        new SanitizationRule(
            Pattern.compile("-----BEGIN [A-Z ]+PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]+PRIVATE KEY-----"),
            "[PRIVATE_KEY_REDACTED]"
        ),

        // Email addresses
        new SanitizationRule(
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
            "[EMAIL_REDACTED]"
        ),

        // IPv4 addresses
        new SanitizationRule(
            Pattern.compile("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b"),
            "[IP_REDACTED]"
        )
    );

    /**
     * Sanitizes {@code rawLog} by replacing all secret patterns with descriptive placeholders.
     *
     * @param rawLog raw log text from user input (may be null)
     * @return sanitized text, or empty string if input is null, or a safe fallback if
     *         sanitization itself fails unexpectedly
     */
    public String sanitize(String rawLog) {
        if (rawLog == null) {
            return "";
        }
        try {
            String result = rawLog;
            for (SanitizationRule rule : RULES) {
                result = rule.pattern().matcher(result).replaceAll(rule.replacement());
            }
            return result;
        } catch (RuntimeException | Error e) {
            log.warn("Log sanitization failed ({}); returning safe fallback. Raw log content NOT logged.",
                    e.getClass().getSimpleName());
            return "[SANITIZATION_FAILED — LOG REDACTED FOR SAFETY]";
        }
    }
}
