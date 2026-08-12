package com.opsera.pipelineassistant.testutil;

import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.security.JwtTokenProvider;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Test utility that creates pre-configured JwtTokenProvider instances and
 * helper methods for generating test tokens with known claims.
 *
 * Used by JwtTokenProviderTest and any test class that needs to produce
 * valid JWT tokens without loading a full Spring context.
 */
public final class JwtTestHelper {

    // 32 ASCII characters = 32 bytes — the minimum for HMAC-SHA256
    public static final String TEST_SECRET = "test-secret-key-for-jwt-tests-xx";

    private JwtTestHelper() {}

    /** Returns a provider configured with the shared test secret and 15-min access expiry. */
    public static JwtTokenProvider buildProvider() {
        return buildProvider(TEST_SECRET, 900L, 86400L);
    }

    /**
     * Returns a provider with a 1-second access/refresh expiry, useful for
     * producing tokens that expire almost immediately in expiry tests.
     */
    public static JwtTokenProvider buildExpiredProvider() {
        return buildProvider(TEST_SECRET, 1L, 1L);
    }

    /** Returns a provider with a different secret, used to produce tokens that fail
     *  signature validation against the default test provider. */
    public static JwtTokenProvider buildAlternateKeyProvider() {
        return buildProvider("alternate-secret-key-for-testing!!", 900L, 86400L);
    }

    /**
     * Builds a JwtTokenProvider with explicit configuration. All fields are set via
     * ReflectionTestUtils so the provider is usable without Spring container.
     */
    public static JwtTokenProvider buildProvider(
            String secret, long accessExpirationSeconds, long refreshExpirationSeconds) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", secret);
        ReflectionTestUtils.setField(provider, "accessTokenExpirationSeconds", accessExpirationSeconds);
        ReflectionTestUtils.setField(provider, "refreshTokenExpirationSeconds", refreshExpirationSeconds);
        provider.init();
        return provider;
    }

    /** Convenience method to generate an access token for the default test analyst user. */
    public static String generateAnalystToken() {
        return buildProvider().generateAccessToken(UserTestFactory.analyst());
    }

    /** Convenience method to generate a token for a given user with the default test provider. */
    public static String generateToken(User user) {
        return buildProvider().generateAccessToken(user);
    }
}
