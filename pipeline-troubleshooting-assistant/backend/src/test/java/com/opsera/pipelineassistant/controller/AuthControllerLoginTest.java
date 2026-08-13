package com.opsera.pipelineassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.pipelineassistant.dto.Responses.LoginResponse;
import com.opsera.pipelineassistant.dto.Responses.LoginResult;
import com.opsera.pipelineassistant.exception.AccountLockedException;
import com.opsera.pipelineassistant.exception.EmailNotVerifiedException;
import com.opsera.pipelineassistant.fixtures.LoginRequestFixtures;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerLoginTest {

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

    private static final String URL = "/api/auth/login";
    private static final LoginResponse PROFILE =
            new LoginResponse("user@example.com", "Test User", "ANALYST", false);
    private static final LoginResult SUCCESS_RESULT =
            new LoginResult("access-jwt", "raw-refresh-token", PROFILE, null);

    // ── AC1/AC2/AC4: success with cookies ────────────────────────────────────

    @Test
    void login_validCredentials_returns200WithProfile() throws Exception {
        when(authService.login(anyString(), anyString())).thenReturn(SUCCESS_RESULT);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequestFixtures.valid())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.displayName").value("Test User"))
                .andExpect(jsonPath("$.role").value("ANALYST"));
    }

    @Test
    void login_validCredentials_setsAccessTokenCookie() throws Exception {
        when(authService.login(anyString(), anyString())).thenReturn(SUCCESS_RESULT);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequestFixtures.valid())))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
                    String accessCookie = cookies.stream()
                            .filter(c -> c.startsWith("access_token="))
                            .findFirst()
                            .orElse(null);
                    assertThat(accessCookie).isNotNull();
                    assertThat(accessCookie).contains("HttpOnly");
                    assertThat(accessCookie).contains("Secure");
                    assertThat(accessCookie).contains("SameSite=Strict");
                    assertThat(accessCookie).contains("Path=/api");
                    assertThat(accessCookie).contains("Max-Age=900");
                });
    }

    @Test
    void login_validCredentials_setsRefreshTokenCookieOnAuthPath() throws Exception {
        when(authService.login(anyString(), anyString())).thenReturn(SUCCESS_RESULT);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequestFixtures.valid())))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
                    String refreshCookie = cookies.stream()
                            .filter(c -> c.startsWith("refresh_token="))
                            .findFirst()
                            .orElse(null);
                    assertThat(refreshCookie).isNotNull();
                    assertThat(refreshCookie).contains("HttpOnly");
                    assertThat(refreshCookie).contains("Secure");
                    assertThat(refreshCookie).contains("SameSite=Strict");
                    assertThat(refreshCookie).contains("Path=/api/auth");
                    assertThat(refreshCookie).contains("Max-Age=604800");
                });
    }

    @Test
    void login_validCredentials_doesNotReturnTokensInBody() throws Exception {
        when(authService.login(anyString(), anyString())).thenReturn(SUCCESS_RESULT);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequestFixtures.valid())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.rawRefreshToken").doesNotExist());
    }

    // ── AC5: invalid credentials → 401 ───────────────────────────────────────

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequestFixtures.valid())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    // ── AC6: unverified email → 403 ───────────────────────────────────────────

    @Test
    void login_unverifiedEmail_returns403() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenThrow(new EmailNotVerifiedException("Please verify your email before logging in"));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequestFixtures.valid())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Please verify your email before logging in"));
    }

    // ── AC7: locked account → 423 ────────────────────────────────────────────

    @Test
    void login_lockedAccount_returns423() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenThrow(new AccountLockedException(
                        "Account locked due to too many failed attempts. Try again in 15 minutes."));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequestFixtures.valid())))
                .andExpect(status().is(423))
                .andExpect(jsonPath("$.message").value(
                        "Account locked due to too many failed attempts. Try again in 15 minutes."));
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void login_missingEmail_returns400() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"SecurePass123!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_invalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequestFixtures.withEmail("not-an-email"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_missingPassword_returns400() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isBadRequest());
    }
}
