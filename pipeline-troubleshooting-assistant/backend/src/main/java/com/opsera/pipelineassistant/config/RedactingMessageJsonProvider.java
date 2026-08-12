package com.opsera.pipelineassistant.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Replaces the default LogstashEncoder message provider with one that redacts sensitive
 * patterns (passwords, Bearer tokens, AWS keys, secrets) before writing to JSON output.
 *
 * Patterns are pre-compiled at class-load time to keep per-event cost low.
 * Configured in logback-spring.xml as a provider within LoggingEventCompositeJsonEncoder.
 */
public class RedactingMessageJsonProvider extends MessageJsonProvider {

    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            // password=<value> — query params, log statements
            Pattern.compile("password=[^\\s,&\"]+", Pattern.CASE_INSENSITIVE),
            // secret=<value>
            Pattern.compile("secret=[^\\s,&\"]+", Pattern.CASE_INSENSITIVE),
            // token=<value> (generic token query param / log field)
            Pattern.compile("token=[^\\s,&\"]+", Pattern.CASE_INSENSITIVE),
            // Authorization: Bearer <jwt>
            Pattern.compile("Bearer [A-Za-z0-9\\-._~+/]+=*"),
            // AWS access key IDs
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            // PEM private key markers
            Pattern.compile("-----BEGIN [A-Z ]+ PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]+ PRIVATE KEY-----")
    );

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        String message = event.getFormattedMessage();
        if (message != null) {
            for (Pattern pattern : SENSITIVE_PATTERNS) {
                message = pattern.matcher(message).replaceAll("[REDACTED]");
            }
        }
        if (message != null) {
            generator.writeStringField(getFieldName(), message);
        } else {
            generator.writeNullField(getFieldName());
        }
    }
}
