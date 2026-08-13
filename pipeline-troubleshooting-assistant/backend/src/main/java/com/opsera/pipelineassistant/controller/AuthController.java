package com.opsera.pipelineassistant.controller;

import com.opsera.pipelineassistant.dto.LoginRequest;
import com.opsera.pipelineassistant.dto.MfaChallengeRequest;
import com.opsera.pipelineassistant.dto.MfaRecoverRequest;
import com.opsera.pipelineassistant.dto.MfaVerifyRequest;
import com.opsera.pipelineassistant.dto.RegisterRequest;
import com.opsera.pipelineassistant.dto.ResendVerificationRequest;
import com.opsera.pipelineassistant.dto.Responses.LoginResponse;
import com.opsera.pipelineassistant.dto.Responses.LoginResult;
import com.opsera.pipelineassistant.dto.Responses.MfaSetupResponse;
import com.opsera.pipelineassistant.dto.Responses.RefreshResult;
import com.opsera.pipelineassistant.dto.Responses.RegisterResponse;
import com.opsera.pipelineassistant.dto.Responses.RegistrationResult;
import com.opsera.pipelineassistant.security.MfaService;
import com.opsera.pipelineassistant.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final MfaService mfaService;
    private final Environment environment;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpirationSeconds;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpirationSeconds;

    @Value("${mfa.challenge-token-expiration:300}")
    private long mfaChallengeTokenExpirationSeconds;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/auth/login");
        LoginResult result = authService.login(request.getEmail(), request.getPassword());

        if (result.challengeToken() != null) {
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.SET_COOKIE,
                    buildCookie("mfa_challenge", result.challengeToken(),
                            Duration.ofSeconds(mfaChallengeTokenExpirationSeconds), "/api/auth/mfa").toString());
            return new ResponseEntity<>(result.profile(), headers, HttpStatus.OK);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE,
                buildCookie("access_token", result.accessToken(),
                        Duration.ofSeconds(accessTokenExpirationSeconds), "/").toString());
        headers.add(HttpHeaders.SET_COOKIE,
                buildCookie("refresh_token", result.rawRefreshToken(),
                        Duration.ofSeconds(refreshTokenExpirationSeconds), "/api/auth").toString());
        return new ResponseEntity<>(result.profile(), headers, HttpStatus.OK);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LoginResponse> me(@AuthenticationPrincipal UserDetails userDetails) {
        log.info("GET /api/auth/me");
        return ResponseEntity.ok(authService.currentUser(userDetails.getUsername()));
    }

    @PostMapping("/mfa/challenge")
    public ResponseEntity<LoginResponse> mfaChallenge(
            @CookieValue(value = "mfa_challenge", required = false) String challengeCookie,
            @Valid @RequestBody MfaChallengeRequest request) {
        log.info("POST /api/auth/mfa/challenge");
        if (challengeCookie == null || challengeCookie.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "No MFA challenge in progress. Please log in again.");
        }
        LoginResult result = authService.verifyMfaChallenge(challengeCookie, request.getCode());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie("mfa_challenge").toString())
                .header(HttpHeaders.SET_COOKIE,
                        buildCookie("access_token", result.accessToken(),
                                Duration.ofSeconds(accessTokenExpirationSeconds)).toString())
                .header(HttpHeaders.SET_COOKIE,
                        buildCookie("refresh_token", result.rawRefreshToken(),
                                Duration.ofSeconds(refreshTokenExpirationSeconds), "/api/auth").toString())
                .body(result.profile());
    }

    @PostMapping("/mfa/recover")
    public ResponseEntity<LoginResponse> mfaRecover(
            @CookieValue(value = "mfa_challenge", required = false) String challengeCookie,
            @Valid @RequestBody MfaRecoverRequest request) {
        log.info("POST /api/auth/mfa/recover");
        if (challengeCookie == null || challengeCookie.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "No MFA challenge in progress. Please log in again.");
        }
        LoginResult result = authService.verifyMfaRecovery(challengeCookie, request.getRecoveryCode());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie("mfa_challenge").toString())
                .header(HttpHeaders.SET_COOKIE,
                        buildCookie("access_token", result.accessToken(),
                                Duration.ofSeconds(accessTokenExpirationSeconds)).toString())
                .header(HttpHeaders.SET_COOKIE,
                        buildCookie("refresh_token", result.rawRefreshToken(),
                                Duration.ofSeconds(refreshTokenExpirationSeconds), "/api/auth").toString())
                .body(result.profile());
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(
            @CookieValue(value = "refresh_token", required = false) String refreshTokenCookie) {
        log.info("POST /api/auth/refresh");
        if (refreshTokenCookie == null || refreshTokenCookie.isBlank()) {
            return expiredResponse();
        }
        try {
            RefreshResult result = authService.refresh(refreshTokenCookie);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE,
                            buildCookie("access_token", result.accessToken(),
                                    Duration.ofSeconds(accessTokenExpirationSeconds)).toString())
                    .header(HttpHeaders.SET_COOKIE,
                            buildCookie("refresh_token", result.refreshToken(),
                                    Duration.ofSeconds(refreshTokenExpirationSeconds)).toString())
                    .body(Map.of("message", "Token refreshed"));
        } catch (Exception e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            return expiredResponse();
        }
    }

    private ResponseEntity<Map<String, String>> expiredResponse() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, clearCookie("access_token").toString())
                .header(HttpHeaders.SET_COOKIE, clearCookie("refresh_token").toString())
                .body(Map.of("message", "Session expired. Please log in again."));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @CookieValue(value = "refresh_token", required = false) String refreshTokenCookie) {
        log.info("POST /api/auth/logout");
        authService.logout(refreshTokenCookie);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie("access_token").toString())
                .header(HttpHeaders.SET_COOKIE, clearCookie("refresh_token").toString())
                .body(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /api/auth/register");
        RegistrationResult result = authService.register(
                request.getEmail(), request.getPassword(), request.getDisplayName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(
                        "Registration successful. Please verify your email.",
                        exposeVerificationUrl() ? result.verificationUrl() : null));
    }

    @GetMapping("/verify")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        log.info("GET /api/auth/verify");
        authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email verified successfully. You can now log in."));
    }

    private boolean exposeVerificationUrl() {
        // Local/dev has no SMTP — surface the link in API responses for the UI.
        return environment.matchesProfiles("dev");
    }

    private ResponseCookie buildCookie(String name, String value, Duration maxAge) {
        return buildCookie(name, value, maxAge, "/");
    }

    private ResponseCookie buildCookie(String name, String value, Duration maxAge, String path) {
        return ResponseCookie.from(name, value)
                .maxAge(maxAge)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(path)
                .build();
    }

    private ResponseCookie clearCookie(String name) {
        return ResponseCookie.from(name, "")
                .maxAge(0)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .build();
    }

    @PostMapping("/verify/resend")
    public ResponseEntity<Map<String, String>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        log.info("POST /api/auth/verify/resend");
        String verificationUrl = authService.resendVerification(request.getEmail());
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", "Verification email sent.");
        if (exposeVerificationUrl() && verificationUrl != null) {
            body.put("verificationUrl", verificationUrl);
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/mfa/setup")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MfaSetupResponse> setupMfa(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/auth/mfa/setup");
        MfaService.MfaSetupData data = mfaService.setupMfa(userDetails.getUsername());
        return ResponseEntity.ok(new MfaSetupResponse(data.qrCodeUri(), data.recoveryCodes()));
    }

    @PostMapping("/mfa/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> verifyMfa(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody MfaVerifyRequest request) {
        log.info("POST /api/auth/mfa/verify");
        mfaService.verifyMfa(userDetails.getUsername(), request.getCode());
        return ResponseEntity.ok(Map.of("message", "MFA enrollment completed successfully"));
    }
}
