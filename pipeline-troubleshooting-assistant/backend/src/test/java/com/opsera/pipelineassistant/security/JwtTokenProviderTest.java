package com.opsera.pipelineassistant.security;

import com.opsera.pipelineassistant.model.Role;
import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.testutil.JwtTestHelper;
import com.opsera.pipelineassistant.testutil.UserTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;
    private User analyst;
    private User kbAdmin;

    @BeforeEach
    void setUp() {
        provider = JwtTestHelper.buildProvider();
        analyst  = UserTestFactory.analyst();
        kbAdmin  = UserTestFactory.kbAdmin();
    }

    // ── generateAccessToken ───────────────────────────────────────────────────

    @Test
    void generateAccessToken_producesThreePartDotSeparatedJwt() {
        String token = provider.generateAccessToken(analyst);

        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        assertThat(parts[0]).isNotBlank(); // header
        assertThat(parts[1]).isNotBlank(); // payload
        assertThat(parts[2]).isNotBlank(); // signature
    }

    @Test
    void generateAccessToken_subjectIsUserEmail() {
        String token = provider.generateAccessToken(analyst);

        assertThat(provider.extractEmail(token)).isEqualTo(analyst.getEmail());
    }

    @Test
    void generateAccessToken_roleClaimMatchesUserRole() {
        String analystToken = provider.generateAccessToken(analyst);
        String adminToken   = provider.generateAccessToken(kbAdmin);

        assertThat(provider.extractRole(analystToken)).isEqualTo(Role.ANALYST.name());
        assertThat(provider.extractRole(adminToken)).isEqualTo(Role.KB_ADMIN.name());
    }

    @Test
    void generateAccessToken_producedTokenPassesValidation() {
        String token = provider.generateAccessToken(analyst);

        assertThat(provider.validateToken(token)).isTrue();
    }

    // ── generateRefreshToken ─────────────────────────────────────────────────

    @Test
    void generateRefreshToken_is64HexCharacters() {
        String token = provider.generateRefreshToken();

        assertThat(token).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void generateRefreshToken_producesUniqueValuesAcrossMultipleCalls() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            tokens.add(provider.generateRefreshToken());
        }
        // All 20 should be distinct — SHA-256 space makes collision probability negligible
        assertThat(tokens).hasSize(20);
    }

    // ── validateToken — valid scenarios ──────────────────────────────────────

    @Test
    void validateToken_returnsTrueForFreshValidToken() {
        String token = provider.generateAccessToken(analyst);

        assertThat(provider.validateToken(token)).isTrue();
    }

    // ── validateToken — rejection scenarios ──────────────────────────────────

    @Test
    void validateToken_returnsFalseForExpiredToken() throws InterruptedException {
        JwtTokenProvider shortLivedProvider = JwtTestHelper.buildExpiredProvider();
        String token = shortLivedProvider.generateAccessToken(analyst);

        // Wait for the 1-second token to expire
        Thread.sleep(1_100);

        assertThat(shortLivedProvider.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_returnsFalseForTokenSignedWithDifferentKey() {
        JwtTokenProvider otherProvider = JwtTestHelper.buildAlternateKeyProvider();
        String tokenFromOtherKey = otherProvider.generateAccessToken(analyst);

        // Verify the token against the default provider — different key should reject
        assertThat(provider.validateToken(tokenFromOtherKey)).isFalse();
    }

    @Test
    void validateToken_returnsFalseForMalformedToken() {
        assertThat(provider.validateToken("not.a.valid.jwt.string")).isFalse();
    }

    @Test
    void validateToken_returnsFalseForRandomString() {
        assertThat(provider.validateToken("completely-random-gibberish")).isFalse();
    }

    @Test
    void validateToken_returnsFalseForNullToken() {
        assertThat(provider.validateToken(null)).isFalse();
    }

    @Test
    void validateToken_returnsFalseForEmptyToken() {
        assertThat(provider.validateToken("")).isFalse();
    }

    @Test
    void validateToken_returnsFalseForBlankToken() {
        assertThat(provider.validateToken("   ")).isFalse();
    }

    @Test
    void validateToken_returnsFalseForTamperedPayload() {
        String original = provider.generateAccessToken(analyst);
        // Replace the payload (middle part) with a modified base64 string
        String[] parts = original.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "tampered" + "." + parts[2];

        assertThat(provider.validateToken(tampered)).isFalse();
    }

    // ── extractEmail / extractRole ────────────────────────────────────────────

    @Test
    void extractEmail_returnsAnalystEmail() {
        String token = provider.generateAccessToken(analyst);

        assertThat(provider.extractEmail(token)).isEqualTo("analyst@example.com");
    }

    @Test
    void extractRole_returnsAnalystRole() {
        String token = provider.generateAccessToken(analyst);

        assertThat(provider.extractRole(token)).isEqualTo("ANALYST");
    }

    @Test
    void extractRole_returnsKbAdminRole() {
        String token = provider.generateAccessToken(kbAdmin);

        assertThat(provider.extractRole(token)).isEqualTo("KB_ADMIN");
    }

    @Test
    void extractEmail_returnsManagerEmail() {
        User manager = UserTestFactory.manager();
        String token = provider.generateAccessToken(manager);

        assertThat(provider.extractEmail(token)).isEqualTo("manager@example.com");
        assertThat(provider.extractRole(token)).isEqualTo("MANAGER");
    }

    // ── @PostConstruct validation ─────────────────────────────────────────────

    @Test
    void init_throwsIllegalStateExceptionWhenSecretIsTooShort() {
        assertThatThrownBy(() -> JwtTestHelper.buildProvider("tooshort", 900L, 86400L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void init_acceptsSecretOfExactly32Bytes() {
        // Should not throw — exactly at the minimum length
        JwtTokenProvider exact32 = JwtTestHelper.buildProvider(
                "12345678901234567890123456789012", 900L, 86400L);
        assertThat(exact32.validateToken(exact32.generateAccessToken(analyst))).isTrue();
    }
}
