package com.opsera.pipelineassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.pipelineassistant.security.AesEncryptionUtil;
import com.opsera.pipelineassistant.security.CustomUserDetailsService;
import com.opsera.pipelineassistant.security.JwtTokenProvider;
import com.opsera.pipelineassistant.security.MfaService;
import com.opsera.pipelineassistant.service.AuthService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
}
