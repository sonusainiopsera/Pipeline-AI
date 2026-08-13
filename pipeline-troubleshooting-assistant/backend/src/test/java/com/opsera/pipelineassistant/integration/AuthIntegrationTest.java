package com.opsera.pipelineassistant.integration;

import com.opsera.pipelineassistant.dto.Responses.LoginResponse;
import com.opsera.pipelineassistant.model.Role;
import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.repository.UserRepository;
import com.opsera.pipelineassistant.service.EmailService;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test suite for the authentication system.
 * Uses Testcontainers PostgreSQL so Flyway migrations run against a real database.
 * EmailService is mocked (no-op) — verification tokens are read directly from the DB.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles({"auth-required", "auth-it"})
class AuthIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private EmailService emailService;

    private static final AtomicInteger USER_COUNTER = new AtomicInteger(0);

    private static final String DEFAULT_PASSWORD = "TestPass123!";

    // ── helpers ────────────────────────────────────────────────────────────────

    private String uniqueEmail() {
        return "user" + USER_COUNTER.incrementAndGet() + "@integration.test";
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpEntity<Object> jsonEntity(Object body, Map<String, String> cookies) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (cookies != null && !cookies.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            cookies.forEach((k, v) -> {
                if (sb.length() > 0) sb.append("; ");
                sb.append(k).append("=").append(v);
            });
            headers.set(HttpHeaders.COOKIE, sb.toString());
        }
        return new HttpEntity<>(body, headers);
    }

    private Map<String, String> parseCookies(ResponseEntity<?> response) {
        Map<String, String> result = new LinkedHashMap<>();
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies != null) {
            for (String header : setCookies) {
                String nameValue = header.split(";")[0];
                int eq = nameValue.indexOf('=');
                if (eq > 0) {
                    String name = nameValue.substring(0, eq).trim();
                    String value = nameValue.substring(eq + 1).trim();
                    if (!value.isEmpty()) {
                        result.put(name, value);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Registers a user and verifies their email via the DB-stored token.
     * Returns the email used.
     */
    private String registerAndVerify(String email) {
        Map<String, String> body = Map.of(
                "email", email,
                "password", DEFAULT_PASSWORD,
                "displayName", "Test User");
        ResponseEntity<Map> regResp = rest.exchange(
                url("/api/auth/register"), HttpMethod.POST,
                jsonEntity(body, null), Map.class);
        assertThat(regResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String token = userRepository.findByEmail(email)
                .map(User::getVerificationToken)
                .orElseThrow(() -> new AssertionError("User not found after registration"));
        ResponseEntity<Map> verifyResp = rest.getForEntity(
                url("/api/auth/verify?token=" + token), Map.class);
        assertThat(verifyResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        return email;
    }

    /**
     * Logs in and returns a cookie map with access_token and refresh_token.
     */
    private Map<String, String> login(String email, String password) {
        Map<String, String> body = Map.of("email", email, "password", password);
        ResponseEntity<LoginResponse> resp = rest.exchange(
                url("/api/auth/login"), HttpMethod.POST,
                jsonEntity(body, null), LoginResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return parseCookies(resp);
    }

    private Map<String, String> login(String email) {
        return login(email, DEFAULT_PASSWORD);
    }

    private String extractSecretFromQrUri(String qrUri) {
        int q = qrUri.indexOf('?');
        if (q < 0) throw new AssertionError("No query in QR URI: " + qrUri);
        for (String param : qrUri.substring(q + 1).split("&")) {
            if (param.startsWith("secret=")) return param.substring("secret=".length());
        }
        throw new AssertionError("No secret param in QR URI: " + qrUri);
    }

    private String generateTotpCode(String secret) {
        try {
            DefaultCodeGenerator gen = new DefaultCodeGenerator();
            long counter = System.currentTimeMillis() / 1000 / 30;
            return gen.generate(secret, counter);
        } catch (Exception e) {
            throw new AssertionError("TOTP generation failed", e);
        }
    }

    // ── AC5: unauthenticated requests return 401 ───────────────────────────────

    @Test
    void unauthenticated_protectedEndpoint_returns401() {
        ResponseEntity<Map> response = rest.exchange(
                url("/api/analyze"), HttpMethod.POST,
                jsonEntity(Map.of("logText", "test"), null), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unauthenticated_historyEndpoint_returns401() {
        ResponseEntity<Map> response = rest.getForEntity(url("/api/history"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── AC2: complete non-MFA lifecycle ────────────────────────────────────────

    @Test
    void fullLifecycle_registerVerifyLoginAnalyzeRefreshLogout() {
        String email = uniqueEmail();
        registerAndVerify(email);

        // login
        Map<String, String> cookies = login(email);
        assertThat(cookies).containsKeys("access_token", "refresh_token");

        // access protected endpoint
        ResponseEntity<Map> analyzeResp = rest.exchange(
                url("/api/analyze"), HttpMethod.POST,
                jsonEntity(Map.of("logText", "BUILD FAILURE: compilation error"), cookies),
                Map.class);
        assertThat(analyzeResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // token refresh (AC7 partially — using current token which is still valid)
        ResponseEntity<Map> refreshResp = rest.exchange(
                url("/api/auth/refresh"), HttpMethod.POST,
                jsonEntity(null, cookies), Map.class);
        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, String> newCookies = parseCookies(refreshResp);
        assertThat(newCookies).containsKey("access_token");

        // logout
        ResponseEntity<Map> logoutResp = rest.exchange(
                url("/api/auth/logout"), HttpMethod.POST,
                jsonEntity(null, newCookies), Map.class);
        assertThat(logoutResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── AC8: logout invalidates refresh token ─────────────────────────────────

    @Test
    void logout_invalidatesRefreshToken() {
        String email = uniqueEmail();
        registerAndVerify(email);
        Map<String, String> cookies = login(email);

        // logout
        rest.exchange(url("/api/auth/logout"), HttpMethod.POST,
                jsonEntity(null, cookies), Map.class);

        // attempt refresh with the same refresh_token → should fail
        ResponseEntity<Map> refreshResp = rest.exchange(
                url("/api/auth/refresh"), HttpMethod.POST,
                jsonEntity(null, Map.of("refresh_token", cookies.getOrDefault("refresh_token", ""))),
                Map.class);
        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── AC7: expired access token → refresh flow ─────────────────────────────

    @Test
    void expiredAccessToken_refreshReturnsNewToken() throws InterruptedException {
        String email = uniqueEmail();
        registerAndVerify(email);
        Map<String, String> cookies = login(email);

        // wait for the 5-second access token to expire (application-auth-it.yml sets 5s)
        Thread.sleep(6_000);

        // expired token should fail for protected endpoint
        ResponseEntity<Map> expiredResp = rest.exchange(
                url("/api/history"), HttpMethod.GET,
                jsonEntity(null, cookies), Map.class);
        assertThat(expiredResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // refresh with still-valid refresh token
        ResponseEntity<Map> refreshResp = rest.exchange(
                url("/api/auth/refresh"), HttpMethod.POST,
                jsonEntity(null, cookies), Map.class);
        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, String> newCookies = parseCookies(refreshResp);
        assertThat(newCookies).containsKey("access_token");

        // new access token should work
        ResponseEntity<Map> historyResp = rest.exchange(
                url("/api/history"), HttpMethod.GET,
                jsonEntity(null, newCookies), Map.class);
        assertThat(historyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── AC3: ANALYST RBAC ─────────────────────────────────────────────────────

    @Test
    void analyst_canReadErrors_cannotWriteErrors() {
        String email = uniqueEmail();
        registerAndVerify(email);
        Map<String, String> cookies = login(email);

        // ANALYST can GET /api/errors
        ResponseEntity<List> getResp = rest.exchange(
                url("/api/errors"), HttpMethod.GET,
                jsonEntity(null, cookies), List.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // ANALYST cannot POST /api/errors
        Map<String, String> errorBody = Map.of(
                "errorPattern", "OutOfMemoryError",
                "category", "Memory",
                "rootCause", "Heap exhausted",
                "solution", "Increase heap",
                "severity", "CRITICAL");
        ResponseEntity<Map> postResp = rest.exchange(
                url("/api/errors"), HttpMethod.POST,
                jsonEntity(errorBody, cookies), Map.class);
        assertThat(postResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void analyst_canAccessAnalyzeAndHistory() {
        String email = uniqueEmail();
        registerAndVerify(email);
        Map<String, String> cookies = login(email);

        ResponseEntity<Map> analyzeResp = rest.exchange(
                url("/api/analyze"), HttpMethod.POST,
                jsonEntity(Map.of("logText", "ERROR: test failure in pipeline"), cookies), Map.class);
        assertThat(analyzeResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> historyResp = rest.exchange(
                url("/api/history"), HttpMethod.GET,
                jsonEntity(null, cookies), Map.class);
        assertThat(historyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── AC4: KB_ADMIN RBAC ────────────────────────────────────────────────────

    @Test
    void kbAdmin_canCreateAndReadErrors() {
        String email = uniqueEmail();
        registerAndVerify(email);

        // elevate to KB_ADMIN directly in DB
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setRole(Role.KB_ADMIN);
            userRepository.save(user);
        });

        Map<String, String> cookies = login(email);

        // KB_ADMIN can GET /api/errors
        ResponseEntity<List> getResp = rest.exchange(
                url("/api/errors"), HttpMethod.GET,
                jsonEntity(null, cookies), List.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // KB_ADMIN can POST /api/errors
        Map<String, String> errorBody = Map.of(
                "errorPattern", "NullPointerException in pipeline stage",
                "category", "Runtime",
                "rootCause", "Null reference dereference",
                "solution", "Add null check before dereference",
                "severity", "HIGH");
        ResponseEntity<Map> postResp = rest.exchange(
                url("/api/errors"), HttpMethod.POST,
                jsonEntity(errorBody, cookies), Map.class);
        assertThat(postResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ── AC6: account lockout after 5 failed attempts ──────────────────────────

    @Test
    void accountLockout_after5FailedAttempts_returns423() {
        String email = uniqueEmail();
        registerAndVerify(email);

        Map<String, String> wrongLogin = Map.of("email", email, "password", "WrongPass123!");

        // 4 failed attempts → each returns 401
        for (int i = 0; i < 4; i++) {
            ResponseEntity<Map> resp = rest.exchange(
                    url("/api/auth/login"), HttpMethod.POST,
                    jsonEntity(wrongLogin, null), Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // 5th attempt triggers lockout
        ResponseEntity<Map> lockResp = rest.exchange(
                url("/api/auth/login"), HttpMethod.POST,
                jsonEntity(wrongLogin, null), Map.class);
        assertThat(lockResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // subsequent attempt with correct credentials → 423 Locked
        Map<String, String> correctLogin = Map.of("email", email, "password", DEFAULT_PASSWORD);
        ResponseEntity<Map> lockedResp = rest.exchange(
                url("/api/auth/login"), HttpMethod.POST,
                jsonEntity(correctLogin, null), Map.class);
        assertThat(lockedResp.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
    }

    // ── AC2: MFA enrollment and login lifecycle ───────────────────────────────

    @Test
    void mfaLifecycle_enrollAndLoginWithTotp() {
        String email = uniqueEmail();
        registerAndVerify(email);
        Map<String, String> cookies = login(email);

        // initiate MFA setup
        ResponseEntity<Map> setupResp = rest.exchange(
                url("/api/auth/mfa/setup"), HttpMethod.POST,
                jsonEntity(null, cookies), Map.class);
        assertThat(setupResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        String qrCodeUri = (String) setupResp.getBody().get("qrCodeUri");
        assertThat(qrCodeUri).isNotBlank();

        // extract secret and generate TOTP code
        String secret = extractSecretFromQrUri(qrCodeUri);
        String totpCode = generateTotpCode(secret);

        // verify MFA enrollment
        ResponseEntity<Map> verifyResp = rest.exchange(
                url("/api/auth/mfa/verify"), HttpMethod.POST,
                jsonEntity(Map.of("code", totpCode), cookies), Map.class);
        assertThat(verifyResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // logout then login again — should now require MFA challenge
        rest.exchange(url("/api/auth/logout"), HttpMethod.POST,
                jsonEntity(null, cookies), Map.class);

        Map<String, String> loginBody = Map.of("email", email, "password", DEFAULT_PASSWORD);
        ResponseEntity<LoginResponse> mfaLoginResp = rest.exchange(
                url("/api/auth/login"), HttpMethod.POST,
                jsonEntity(loginBody, null), LoginResponse.class);
        assertThat(mfaLoginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mfaLoginResp.getBody()).isNotNull();
        assertThat(mfaLoginResp.getBody().mfaRequired()).isTrue();

        // complete MFA challenge with fresh TOTP code
        Map<String, String> challengeCookies = parseCookies(mfaLoginResp);
        assertThat(challengeCookies).containsKey("mfa_challenge");

        String challengeCode = generateTotpCode(secret);
        ResponseEntity<LoginResponse> challengeResp = rest.exchange(
                url("/api/auth/mfa/challenge"), HttpMethod.POST,
                jsonEntity(Map.of("code", challengeCode), challengeCookies), LoginResponse.class);
        assertThat(challengeResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, String> finalCookies = parseCookies(challengeResp);
        assertThat(finalCookies).containsKey("access_token");

        // access a protected endpoint with MFA-verified tokens
        ResponseEntity<Map> analyzeResp = rest.exchange(
                url("/api/analyze"), HttpMethod.POST,
                jsonEntity(Map.of("logText", "MFA test log entry"), finalCookies), Map.class);
        assertThat(analyzeResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── AC11: test data is self-contained (no manual DB setup needed) ─────────

    @Test
    void registration_withDuplicateEmail_returnsConflict() {
        String email = uniqueEmail();
        Map<String, String> body = Map.of(
                "email", email, "password", DEFAULT_PASSWORD, "displayName", "Test");

        ResponseEntity<Map> first = rest.exchange(
                url("/api/auth/register"), HttpMethod.POST,
                jsonEntity(body, null), Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> second = rest.exchange(
                url("/api/auth/register"), HttpMethod.POST,
                jsonEntity(body, null), Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
