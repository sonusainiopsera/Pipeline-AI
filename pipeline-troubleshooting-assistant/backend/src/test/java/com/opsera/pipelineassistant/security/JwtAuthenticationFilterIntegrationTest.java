package com.opsera.pipelineassistant.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.Cookie;
import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test verifying JwtAuthenticationFilter end-to-end via a dedicated
 * test controller with a security configuration that enforces authentication.
 * Separate from the main SecurityConfig's permitAll() posture.
 */
@WebMvcTest(JwtAuthenticationFilterIntegrationTest.SecuredTestController.class)
@Import({JwtAuthenticationFilter.class, JwtAuthenticationFilterIntegrationTest.TestSecurityConfig.class})
class JwtAuthenticationFilterIntegrationTest {

    /** Minimal controller exposed only in this test context. */
    @RestController
    @RequestMapping("/test")
    public static class SecuredTestController {
        @GetMapping("/secured")
        public ResponseEntity<String> secured() {
            return ResponseEntity.ok("authenticated");
        }
    }

    /** Replaces the application's permitAll security config for /test/** paths. */
    public static class TestSecurityConfig {
        @Bean
        @Order(-1)
        public SecurityFilterChain testFilterChain(HttpSecurity http,
                                                    JwtAuthenticationFilter jwtFilter) throws Exception {
            http
                    .securityMatcher("/test/**")
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(ex -> ex.authenticationEntryPoint(
                            (req, res, e) -> res.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED)));
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String TEST_EMAIL  = "analyst@example.com";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ── 401 without token ─────────────────────────────────────────────────────

    @Test
    void requestWithoutToken_returns401() throws Exception {
        mockMvc.perform(get("/test/secured"))
                .andExpect(status().isUnauthorized());
    }

    // ── 200 with valid cookie token ───────────────────────────────────────────

    @Test
    void requestWithValidCookieToken_returns200() throws Exception {
        UserDetails userDetails = User.builder()
                .username(TEST_EMAIL)
                .password("hash")
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANALYST")))
                .build();

        when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.extractEmail(VALID_TOKEN)).thenReturn(TEST_EMAIL);
        when(customUserDetailsService.loadUserByUsername(TEST_EMAIL)).thenReturn(userDetails);

        mockMvc.perform(get("/test/secured")
                        .cookie(new Cookie("access_token", VALID_TOKEN)))
                .andExpect(status().isOk());
    }

    // ── 401 with invalid token ─────────────────────────────────────────────────

    @Test
    void requestWithInvalidToken_returns401() throws Exception {
        when(jwtTokenProvider.validateToken("invalid.token")).thenReturn(false);

        mockMvc.perform(get("/test/secured")
                        .cookie(new Cookie("access_token", "invalid.token")))
                .andExpect(status().isUnauthorized());
    }
}
