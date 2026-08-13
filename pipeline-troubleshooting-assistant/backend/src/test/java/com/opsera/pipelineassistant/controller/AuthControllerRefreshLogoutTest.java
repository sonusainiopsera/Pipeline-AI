package com.opsera.pipelineassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.pipelineassistant.dto.Responses.RefreshResult;
import com.opsera.pipelineassistant.security.CustomUserDetailsService;
import com.opsera.pipelineassistant.security.JwtTokenProvider;
import com.opsera.pipelineassistant.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerRefreshLogoutTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String ACCESS_COOKIE = "access_token";
    private static final String RAW_REFRESH = "aabbccddeeff0011223344";

    // ── POST /api/auth/refresh ────────────────────────────────────────────────

    @Test
    void refresh_validCookie_returns200WithNewCookies() throws Exception {
        when(authService.refresh(RAW_REFRESH))
                .thenReturn(new RefreshResult("new-access-jwt", "new-refresh-raw"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE, RAW_REFRESH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Token refreshed"));
    }

    @Test
    void refresh_noCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Session expired. Please log in again."));
    }

    @Test
    void refresh_noCookie_clearsCookies() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> {
                    String setCookie = result.getResponse().getHeader("Set-Cookie");
                    assert setCookie != null && setCookie.contains("Max-Age=0");
                });
    }

    @Test
    void refresh_expiredToken_returns401AndClearsCookies() throws Exception {
        when(authService.refresh(RAW_REFRESH))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Session expired. Please log in again."));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE, RAW_REFRESH)))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> {
                    String setCookie = result.getResponse().getHeader("Set-Cookie");
                    assert setCookie != null && setCookie.contains("Max-Age=0");
                });
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        when(authService.refresh(anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Session expired. Please log in again."));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE, "bad-token")))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/auth/logout ─────────────────────────────────────────────────

    @Test
    void logout_validCookie_returns200() throws Exception {
        doNothing().when(authService).logout(RAW_REFRESH);

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie(REFRESH_COOKIE, RAW_REFRESH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    @Test
    void logout_noCookie_returns200() throws Exception {
        doNothing().when(authService).logout(null);

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    @Test
    void logout_clearsBothCookiesWithMaxAge0() throws Exception {
        doNothing().when(authService).logout(RAW_REFRESH);

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie(REFRESH_COOKIE, RAW_REFRESH)))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    java.util.List<String> setCookies =
                            result.getResponse().getHeaders("Set-Cookie");
                    long cleared = setCookies.stream()
                            .filter(h -> h.contains("Max-Age=0"))
                            .count();
                    assert cleared >= 2 : "Expected both cookies cleared but got: " + setCookies;
                });
    }

    @Test
    void logout_noCookie_stillClearsCookies() throws Exception {
        doNothing().when(authService).logout(null);

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String setCookie = result.getResponse().getHeader("Set-Cookie");
                    assert setCookie != null && setCookie.contains("Max-Age=0");
                });
    }
}
