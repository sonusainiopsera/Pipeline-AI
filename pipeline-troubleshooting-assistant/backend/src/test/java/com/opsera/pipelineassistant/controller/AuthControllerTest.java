package com.opsera.pipelineassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.pipelineassistant.dto.Responses.RegistrationResult;
import com.opsera.pipelineassistant.dto.RegisterRequest;
import com.opsera.pipelineassistant.fixtures.RegisterRequestFixtures;
import com.opsera.pipelineassistant.model.User;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

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

    // ── POST /api/auth/register ───────────────────────────────────────────────

    @Test
    void register_validRequest_returns201WithMessage() throws Exception {
        when(authService.register(anyString(), anyString(), anyString()))
                .thenReturn(new RegistrationResult(
                        User.builder().email("user@example.com").build(),
                        "http://localhost:5173/api/auth/verify?token=abc"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Registration successful. Please verify your email."));
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"SecurePass123!\",\"displayName\":\"Test\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_passwordTooShort_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"Short1!\",\"displayName\":\"Test\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_passwordMissingComplexity_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"alllowercase123!\",\"displayName\":\"Test\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_missingDisplayName_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"SecurePass123!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        when(authService.register(anyString(), anyString(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                        "Registration could not be completed. Please try a different email."));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationJson()))
                .andExpect(status().isConflict());
    }

    @Test
    void register_serviceThrowsBadRequest_returns400() throws Exception {
        when(authService.register(anyString(), anyString(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationJson()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String validRegistrationJson() throws Exception {
        return objectMapper.writeValueAsString(RegisterRequestFixtures.valid());
    }
}
