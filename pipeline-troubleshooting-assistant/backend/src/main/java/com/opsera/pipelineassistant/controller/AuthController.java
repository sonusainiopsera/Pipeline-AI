package com.opsera.pipelineassistant.controller;

import com.opsera.pipelineassistant.dto.LoginRequest;
import com.opsera.pipelineassistant.dto.MfaVerifyRequest;
import com.opsera.pipelineassistant.dto.RegisterRequest;
import com.opsera.pipelineassistant.dto.ResendVerificationRequest;
import com.opsera.pipelineassistant.dto.Responses.LoginResponse;
import com.opsera.pipelineassistant.dto.Responses.LoginResult;
import com.opsera.pipelineassistant.dto.Responses.MfaSetupResponse;
import com.opsera.pipelineassistant.dto.Responses.RefreshResult;
import com.opsera.pipelineassistant.dto.Responses.RegisterResponse;
import com.opsera.pipelineassistant.security.MfaService;
import com.opsera.pipelineassistant.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final MfaService mfaService;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpirationSeconds;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpirationSeconds;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/auth/login");
        LoginResult result = authService.login(request.getEmail(), request.getPassword());
        return ResponseEntity.ok()
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
        authService.register(request.getEmail(), request.getPassword(), request.getDisplayName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse("Registration successful. Please verify your email."));
    }

    @GetMapping("/verify")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        log.info("GET /api/auth/verify");
        authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email verified successfully. You can now log in."));
    }

    private ResponseCookie buildCookie(String name, String value, Duration maxAge) {
        return buildCookie(name, value, maxAge, "/api");
    }

    private ResponseCookie buildCookie(String name, String value, Duration maxAge, String path) {
        return ResponseCookie.from(name, value)
                .maxAge(maxAge)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(path)
                .build();
    }

    private ResponseCookie clearCookie(String name) {
        return ResponseCookie.from(name, "")
                .maxAge(0)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api")
                .build();
    }

    @PostMapping("/verify/resend")
    public ResponseEntity<Map<String, String>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        log.info("POST /api/auth/verify/resend");
        authService.resendVerification(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "Verification email sent."));
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
