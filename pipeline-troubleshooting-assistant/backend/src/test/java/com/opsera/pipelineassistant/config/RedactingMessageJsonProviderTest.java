package com.opsera.pipelineassistant.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedactingMessageJsonProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RedactingMessageJsonProvider provider = new RedactingMessageJsonProvider();

    private JsonNode serialize(String message) throws Exception {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn(message);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (JsonGenerator gen = MAPPER.getFactory().createGenerator(os)) {
            gen.writeStartObject();
            provider.writeTo(gen, event);
            gen.writeEndObject();
        }
        return MAPPER.readTree(os.toString(StandardCharsets.UTF_8));
    }

    private String messageField(String rawMessage) throws Exception {
        return serialize(rawMessage).get("message").asText();
    }

    // ── Sensitive patterns are redacted ───────────────────────────────────────

    @Test
    void bearerTokenIsRedacted() throws Exception {
        String output = messageField("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.abc");
        assertThat(output).contains("[REDACTED]").doesNotContain("eyJhbGciOiJIUzI1NiJ9");
    }

    @Test
    void awsAccessKeyIsRedacted() throws Exception {
        String output = messageField("Using AWS key AKIAIOSFODNN7EXAMPLE to access S3");
        assertThat(output).contains("[REDACTED]").doesNotContain("AKIAIOSFODNN7EXAMPLE");
    }

    @Test
    void passwordParamIsRedacted() throws Exception {
        String output = messageField("Login attempt with password=s3cr3tP@ssw0rd for user admin");
        assertThat(output).contains("[REDACTED]").doesNotContain("s3cr3tP@ssw0rd");
    }

    @Test
    void secretParamIsRedacted() throws Exception {
        String output = messageField("Client configured with secret=my-client-secret123");
        assertThat(output).contains("[REDACTED]").doesNotContain("my-client-secret123");
    }

    @Test
    void tokenParamIsRedacted() throws Exception {
        String output = messageField("Reset link sent with token=abc123xyz to user@example.com");
        assertThat(output).contains("[REDACTED]").doesNotContain("abc123xyz");
    }

    // ── Non-sensitive messages are unchanged ─────────────────────────────────

    @Test
    void plainMessageIsNotModified() throws Exception {
        String plain = "User alice@example.com logged in from 192.168.1.1";
        String output = messageField(plain);
        assertThat(output).isEqualTo(plain);
    }

    @Test
    void nullMessageIsWrittenAsNull() throws Exception {
        JsonNode node = serialize(null);
        assertThat(node.get("message").isNull()).isTrue();
    }

    // ── Output is valid JSON ──────────────────────────────────────────────────

    @Test
    void outputIsValidJsonEvenWhenMessageContainsQuotes() throws Exception {
        String msgWithQuotes = "User said: \"hello world\"";
        // If JSON escaping fails, MAPPER.readTree would throw — the assertion below
        // would never be reached. The serialize() helper verifies valid JSON.
        String output = messageField(msgWithQuotes);
        assertThat(output).isEqualTo(msgWithQuotes);
    }

    @Test
    void messageFieldNameIsMessage() throws Exception {
        JsonNode node = serialize("hello");
        assertThat(node.has("message")).isTrue();
    }
}
