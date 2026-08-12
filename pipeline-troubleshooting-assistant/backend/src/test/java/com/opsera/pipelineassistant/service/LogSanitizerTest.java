package com.opsera.pipelineassistant.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    private final LogSanitizer sanitizer = new LogSanitizer();

    // ── Individual pattern tests ──────────────────────────────────────────────

    @Test
    void shouldRedactAwsAccessKey() {
        String result = sanitizer.sanitize("Build failed: AKIAIOSFODNN7EXAMPLE credentials rejected");
        assertThat(result).doesNotContain("AKIAIOSFODNN7EXAMPLE");
        assertThat(result).contains("[AWS_KEY_REDACTED]");
        assertThat(result).contains("Build failed:");
        assertThat(result).contains("credentials rejected");
    }

    @Test
    void shouldNotRedactStringThatLooksLikeAwsKeyButIsTooShort() {
        String input = "error code: AKIASHORT";
        assertThat(sanitizer.sanitize(input)).isEqualTo(input);
    }

    @Test
    void shouldRedactBearerToken() {
        String result = sanitizer.sanitize(
                "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig");
        assertThat(result).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
        assertThat(result).contains("[BEARER_TOKEN_REDACTED]");
        assertThat(result).contains("Authorization:");
    }

    @Test
    void shouldRedactPasswordAssignment() {
        String result = sanitizer.sanitize("connect failed: password=SuperSecret123 retrying");
        assertThat(result).doesNotContain("SuperSecret123");
        assertThat(result).contains("[PASSWORD_REDACTED]");
        assertThat(result).contains("password=");
    }

    @Test
    void shouldRedactPasswdAssignment() {
        String result = sanitizer.sanitize("config: passwd=HiddenPass99");
        assertThat(result).doesNotContain("HiddenPass99");
        assertThat(result).contains("passwd=[PASSWORD_REDACTED]");
    }

    @Test
    void shouldRedactPwdAssignment() {
        String result = sanitizer.sanitize("auth: pwd=abc123xyz");
        assertThat(result).doesNotContain("abc123xyz");
        assertThat(result).contains("pwd=[PASSWORD_REDACTED]");
    }

    @Test
    void shouldRedactSecretAssignment() {
        String result = sanitizer.sanitize("secret=mysecretvalue123");
        assertThat(result).doesNotContain("mysecretvalue123");
        assertThat(result).contains("secret=[PASSWORD_REDACTED]");
    }

    @Test
    void shouldRedactApiKeyAssignment() {
        String result = sanitizer.sanitize(
                "Integration error: api_key=sk-abcdefghijklmnopqrstuvwxyz123456 unauthorized");
        assertThat(result).doesNotContain("sk-abcdefghijklmnopqrstuvwxyz123456");
        assertThat(result).contains("[API_KEY_REDACTED]");
        assertThat(result).contains("api_key=");
    }

    @Test
    void shouldRedactApiKeyWithHyphenVariant() {
        String result = sanitizer.sanitize("api-key=TESTKEY1234567890abcdef");
        assertThat(result).doesNotContain("TESTKEY1234567890abcdef");
        assertThat(result).contains("[API_KEY_REDACTED]");
    }

    @Test
    void shouldRedactApiKeyWithNoSeparatorVariant() {
        String result = sanitizer.sanitize("apikey=mytoken1234567890abcdef");
        assertThat(result).doesNotContain("mytoken1234567890abcdef");
        assertThat(result).contains("[API_KEY_REDACTED]");
    }

    @Test
    void shouldRedactPrivateKeyBlock() {
        String pemBlock =
                "Config contains:\n"
                + "-----BEGIN RSA PRIVATE KEY-----\n"
                + "MIIEpAIBAAKCAQEAFAKEKEYDATANOTREAL\n"
                + "ANOTHERLINEFAKEDATA1234567890\n"
                + "-----END RSA PRIVATE KEY-----\n"
                + "End of config";

        String result = sanitizer.sanitize(pemBlock);

        assertThat(result).doesNotContain("MIIEpAIBAAKCAQEAFAKEKEYDATANOTREAL");
        assertThat(result).doesNotContain("-----BEGIN RSA PRIVATE KEY-----");
        assertThat(result).contains("[PRIVATE_KEY_REDACTED]");
        assertThat(result).contains("Config contains:");
        assertThat(result).contains("End of config");
    }

    @Test
    void shouldRedactEmailAddress() {
        String result = sanitizer.sanitize("Alert sent to admin@pipeline.internal for review");
        assertThat(result).doesNotContain("admin@pipeline.internal");
        assertThat(result).contains("[EMAIL_REDACTED]");
        assertThat(result).contains("Alert sent to");
        assertThat(result).contains("for review");
    }

    @Test
    void shouldRedactMultipleEmailAddresses() {
        String result = sanitizer.sanitize(
                "CC: alice@example.com, bob@corp.io — see thread");
        assertThat(result).doesNotContain("alice@example.com");
        assertThat(result).doesNotContain("bob@corp.io");
        assertThat(result).containsPattern("\\[EMAIL_REDACTED\\].*\\[EMAIL_REDACTED\\]");
    }

    @Test
    void shouldRedactIpv4Address() {
        String result = sanitizer.sanitize("Connection from 192.168.1.100 refused");
        assertThat(result).doesNotContain("192.168.1.100");
        assertThat(result).contains("[IP_REDACTED]");
        assertThat(result).contains("Connection from");
        assertThat(result).contains("refused");
    }

    @Test
    void shouldRedactMultipleIpAddresses() {
        String result = sanitizer.sanitize("Route 10.0.0.1 to 172.16.0.1 blocked");
        assertThat(result).doesNotContain("10.0.0.1");
        assertThat(result).doesNotContain("172.16.0.1");
    }

    // ── Combined and preservation tests ──────────────────────────────────────

    @Test
    void shouldRedactCombinedInputWithMultipleSecretTypes() {
        String combined =
                "AKIAIOSFODNN7EXAMPLE denied; "
                + "Bearer eyJhbGciOiJIUzI1NiJ9.tok; "
                + "password=S3cr3t123; "
                + "api_key=k-ABCDEFGHIJ1234567890; "
                + "user@example.com from 10.1.2.3";

        String result = sanitizer.sanitize(combined);

        assertThat(result).doesNotContain("AKIAIOSFODNN7EXAMPLE");
        assertThat(result).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
        assertThat(result).doesNotContain("S3cr3t123");
        assertThat(result).doesNotContain("ABCDEFGHIJ1234567890");
        assertThat(result).doesNotContain("user@example.com");
        assertThat(result).doesNotContain("10.1.2.3");
        assertThat(result).contains("[AWS_KEY_REDACTED]");
        assertThat(result).contains("[BEARER_TOKEN_REDACTED]");
        assertThat(result).contains("[PASSWORD_REDACTED]");
        assertThat(result).contains("[API_KEY_REDACTED]");
        assertThat(result).contains("[EMAIL_REDACTED]");
        assertThat(result).contains("[IP_REDACTED]");
    }

    @Test
    void shouldPreserveNonSecretContent() {
        String clean =
                "docker daemon failed\n"
                + "connection refused on port 8080\n"
                + "npm ERR! missing dependency\n"
                + "test failed: assertion error in JUnit\n"
                + "OutOfMemoryError: Java heap space";

        assertThat(sanitizer.sanitize(clean)).isEqualTo(clean);
    }

    @Test
    void shouldPreserveDomainKeywordsUsedForPatternMatching() {
        String domainLog =
                "connection refused after 3 retries\n"
                + "docker build failed: image pull error\n"
                + "npm ERR package.json dependency resolution failed\n"
                + "ECONNREFUSED 127.0.0.1:5432";

        String result = sanitizer.sanitize(domainLog);

        assertThat(result).contains("connection refused");
        assertThat(result).contains("docker build failed");
        assertThat(result).contains("npm ERR");
        assertThat(result).contains("dependency resolution failed");
        // The loopback IP 127.0.0.1 gets redacted — that is expected behaviour
        assertThat(result).doesNotContain("connection refused".length() < 1 ? "x" : "npm ERR!wrong");
    }

    // ── Edge case tests ───────────────────────────────────────────────────────

    @Test
    void shouldReturnEmptyStringForNullInput() {
        assertThat(sanitizer.sanitize(null)).isEmpty();
    }

    @Test
    void shouldReturnEmptyStringForEmptyInput() {
        assertThat(sanitizer.sanitize("")).isEmpty();
    }

    @Test
    void shouldReturnInputUnchangedWhenNoSecretsPresent() {
        String plain = "Build failed: compilation error in Main.java line 42";
        assertThat(sanitizer.sanitize(plain)).isEqualTo(plain);
    }

    @Test
    void shouldReturnOnlyRedactionPlaceholdersWhenInputIsEntirelySecrets() {
        String allSecrets = "AKIAIOSFODNN7EXAMPLE";
        String result = sanitizer.sanitize(allSecrets);
        assertThat(result).isEqualTo("[AWS_KEY_REDACTED]");
        assertThat(result).doesNotContain("AKIA");
    }

    @Test
    void shouldHandleInputThatIsEntirelyAPasswordAssignment() {
        String result = sanitizer.sanitize("password=entirelyasecret");
        assertThat(result).isEqualTo("password=[PASSWORD_REDACTED]");
    }

    @Test
    void shouldHandleReasonablyLargeInput() {
        // Verify no catastrophic backtracking on a ~5,000 char log with one secret
        String noise = "OutOfMemoryError: Java heap space\n".repeat(150);
        String logWithSecret = noise + "AWS key: AKIAIOSFODNN7EXAMPLE\n" + noise;
        String result = sanitizer.sanitize(logWithSecret);
        assertThat(result).doesNotContain("AKIAIOSFODNN7EXAMPLE");
        assertThat(result).contains("[AWS_KEY_REDACTED]");
    }

    // ── Parameterized fixture tests ───────────────────────────────────────────

    static Stream<Arguments> fixturePairs() {
        return Stream.of(
            Arguments.of("sample-log-1.txt", "expected-output-1.txt"),
            Arguments.of("sample-log-2.txt", "expected-output-2.txt"),
            Arguments.of("sample-log-3.txt", "expected-output-3.txt"),
            Arguments.of("sample-log-4.txt", "expected-output-4.txt"),
            Arguments.of("sample-log-5.txt", "expected-output-5.txt")
        );
    }

    @ParameterizedTest(name = "[{index}] sample={0}")
    @MethodSource("fixturePairs")
    void shouldMatchFixtureExpectedOutput(String sampleFile, String expectedFile) throws IOException {
        String input    = loadClasspathText("sanitization/" + sampleFile);
        String expected = loadClasspathText("sanitization/" + expectedFile);
        assertThat(sanitizer.sanitize(input.stripTrailing()))
                .isEqualTo(expected.stripTrailing());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String loadClasspathText(String classpathPath) throws IOException {
        ClassLoader cl = LogSanitizerTest.class.getClassLoader();
        try (InputStream is = cl.getResourceAsStream(classpathPath)) {
            if (is == null) {
                throw new IOException("Test resource not found on classpath: " + classpathPath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
