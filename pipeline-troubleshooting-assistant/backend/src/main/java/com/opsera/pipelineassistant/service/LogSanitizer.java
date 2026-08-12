package com.opsera.pipelineassistant.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class LogSanitizer {

    private static final Pattern AWS_KEY_PATTERN =
            Pattern.compile("AKIA[0-9A-Z]{16}");

    private static final Pattern BEARER_TOKEN_PATTERN =
            Pattern.compile("Bearer\\s+[A-Za-z0-9\\-_.]+");

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("(?i)(password|passwd|secret)=\\S+");

    public String sanitize(String rawLog) {
        if (rawLog == null) {
            return "";
        }
        String result = rawLog;
        result = AWS_KEY_PATTERN.matcher(result).replaceAll("[AWS_KEY_REDACTED]");
        result = BEARER_TOKEN_PATTERN.matcher(result).replaceAll("[BEARER_TOKEN_REDACTED]");
        result = PASSWORD_PATTERN.matcher(result).replaceAll("$1=[PASSWORD_REDACTED]");
        return result;
    }
}
