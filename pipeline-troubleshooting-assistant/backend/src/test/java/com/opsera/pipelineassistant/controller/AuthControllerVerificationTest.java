package com.opsera.pipelineassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.pipelineassistant.dto.ResendVerificationRequest;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerVerificationTest {

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

    @Test
    void verifyEmail_validToken_returns200() throws Exception {
        doNothing().when(authService).verifyEmail("valid-token");

        mockMvc.perform(get("/api/auth/verify").param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified successfully. You can now log in."));
    }

    @Test
    void verifyEmail_invalidToken_returns400() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification link."))
                .when(authService).verifyEmail("bad-token");

        mockMvc.perform(get("/api/auth/verify").param("token", "bad-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmail_expiredToken_returns410() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.GONE,
                "Verification link has expired. Please request a new one."))
                .when(authService).verifyEmail("expired-token");

        mockMvc.perform(get("/api/auth/verify").param("token", "expired-token"))
                .andExpect(status().isGone());
    }

    @Test
    void verifyEmail_missingTokenParam_returns400() throws Exception {
        mockMvc.perform(get("/api/auth/verify"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resendVerification_validEmail_returns200() throws Exception {
        when(authService.resendVerification("user@example.com")).thenReturn(null);

        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail("user@example.com");

        mockMvc.perform(post("/api/auth/verify/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Verification email sent."));
    }

    @Test
    void resendVerification_invalidEmail_returns400() throws Exception {
        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail("not-an-email");

        mockMvc.perform(post("/api/auth/verify/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resendVerification_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/verify/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
