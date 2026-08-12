package com.opsera.pipelineassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.pipelineassistant.dto.Responses.LoginResponse;
import com.opsera.pipelineassistant.dto.Responses.LoginResult;
import com.opsera.pipelineassistant.security.AesEncryptionUtil;
import com.opsera.pipelineassistant.security.CustomUserDetailsService;
import com.opsera.pipelineassistant.security.JwtTokenProvider;
import com.opsera.pipelineassistant.security.MfaService;
import com.opsera.pipelineassistant.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerMfaTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private MfaService mfaService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private AesEncryptionUtil aesEncryptionUtil;

    // ── POST /api/auth/mfa/setup ──────────────────────────────────────────────

    @Test
    @WithMockUser(username = "user@example.com", roles = "ANALYST")
    void setup_authenticatedUser_returns200WithQrUriAndCodes() throws Exception {
        MfaService.MfaSetupData data = new MfaService.MfaSetupData(
                "otpauth://totp/PipelineAssistant:user@example.com?secret=SECRET",
                List.of("ABCD1234", "EFGH5678", "IJKL9012", "MNOP3456",
                        "QRST7890", "UVWX1234", "YZ012345", "ABCDEFGH"));
        when(mfaService.setupMfa("user@example.com")).thenReturn(data);

        mockMvc.perform(post("/api/auth/mfa/setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrCodeUri").value(
                        "otpauth://totp/PipelineAssistant:user@example.com?secret=SECRET"))
                .andExpect(jsonPath("$.recoveryCodes").isArray())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(8));
    }

    @Test
    void setup_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/mfa/setup"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "ANALYST")
    void setup_whenMfaAlreadyEnabled_returns400() throws Exception {
        when(mfaService.setupMfa("user@example.com"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "MFA is already enabled for this account"));

        mockMvc.perform(post("/api/auth/mfa/setup"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/mfa/verify ─────────────────────────────────────────────

    @Test
    @WithMockUser(username = "user@example.com", roles = "ANALYST")
    void verify_validCode_returns200() throws Exception {
        mockMvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("MFA enrollment completed successfully"));

        verify(mfaService).verifyMfa("user@example.com", "123456");
    }

    @Test
    void verify_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "ANALYST")
    void verify_invalidCode_returns401() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid TOTP code"))
                .when(mfaService).verifyMfa(anyString(), anyString());

        mockMvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "ANALYST")
    void verify_expiredSession_returns410() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.GONE,
                "MFA setup session has expired. Please start over."))
                .when(mfaService).verifyMfa(anyString(), anyString());

        mockMvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isGone());
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "ANALYST")
    void verify_nonNumericCode_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ABCDEF\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "ANALYST")
    void verify_codeWithWrongLength_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"12345\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/mfa/challenge ──────────────────────────────────────────

    private static final LoginResponse SUCCESS_PROFILE =
            new LoginResponse("user@example.com", "Test User", "ANALYST", false);
    private static final LoginResult SUCCESS_LOGIN =
            new LoginResult("access-jwt", "raw-refresh", SUCCESS_PROFILE, null);

    @Test
    void challenge_validCodeWithCookie_returns200WithTokenCookies() throws Exception {
        when(authService.verifyMfaChallenge(eq("challenge-token"), eq("123456")))
                .thenReturn(SUCCESS_LOGIN);

        mockMvc.perform(post("/api/auth/mfa/challenge")
                        .cookie(new Cookie("mfa_challenge", "challenge-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(result -> {
                    List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
                    assertThat(cookies.stream().anyMatch(c -> c.startsWith("access_token="))).isTrue();
                    assertThat(cookies.stream().anyMatch(c -> c.startsWith("refresh_token="))).isTrue();
                });
    }

    @Test
    void challenge_missingCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/mfa/challenge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void challenge_invalidCode_returns401() throws Exception {
        when(authService.verifyMfaChallenge(anyString(), eq("000000")))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication code"));

        mockMvc.perform(post("/api/auth/mfa/challenge")
                        .cookie(new Cookie("mfa_challenge", "challenge-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void challenge_expiredChallengeToken_returns401() throws Exception {
        when(authService.verifyMfaChallenge(anyString(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Invalid or expired MFA challenge token. Please log in again."));

        mockMvc.perform(post("/api/auth/mfa/challenge")
                        .cookie(new Cookie("mfa_challenge", "expired-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void challenge_nonNumericCode_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/mfa/challenge")
                        .cookie(new Cookie("mfa_challenge", "challenge-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ABCDEF\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/mfa/recover ────────────────────────────────────────────

    @Test
    void recover_validRecoveryCodeWithCookie_returns200WithTokenCookies() throws Exception {
        when(authService.verifyMfaRecovery(eq("challenge-token"), eq("ABCD1234")))
                .thenReturn(SUCCESS_LOGIN);

        mockMvc.perform(post("/api/auth/mfa/recover")
                        .cookie(new Cookie("mfa_challenge", "challenge-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryCode\":\"ABCD1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(result -> {
                    List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
                    assertThat(cookies.stream().anyMatch(c -> c.startsWith("access_token="))).isTrue();
                });
    }

    @Test
    void recover_missingCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/mfa/recover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryCode\":\"ABCD1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recover_invalidRecoveryCode_returns401() throws Exception {
        when(authService.verifyMfaRecovery(anyString(), eq("WRONGCODE")))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid recovery code"));

        mockMvc.perform(post("/api/auth/mfa/recover")
                        .cookie(new Cookie("mfa_challenge", "challenge-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryCode\":\"WRONGCODE\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recover_allCodesExhausted_returns423() throws Exception {
        when(authService.verifyMfaRecovery(anyString(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.LOCKED,
                        "All recovery codes have been used. Please contact your administrator."));

        mockMvc.perform(post("/api/auth/mfa/recover")
                        .cookie(new Cookie("mfa_challenge", "challenge-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryCode\":\"ABCD1234\"}"))
                .andExpect(status().is(423));
    }

    @Test
    void recover_missingRecoveryCode_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/mfa/recover")
                        .cookie(new Cookie("mfa_challenge", "challenge-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
