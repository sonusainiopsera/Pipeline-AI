package com.opsera.pipelineassistant.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;
    private UserDetails mockUserDetails;

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String TEST_EMAIL  = "analyst@example.com";

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new org.springframework.mock.web.MockFilterChain();
        mockUserDetails = User.builder()
                .username(TEST_EMAIL)
                .password("hash")
                .authorities("ROLE_ANALYST")
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── shouldNotFilter ──────────────────────────────────────────────────────────

    @Test
    void shouldNotFilter_returnsTrueForApiAuthPath() throws Exception {
        request.setServletPath("/api/auth/login");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_returnsTrueForApiAuthSubPath() throws Exception {
        request.setServletPath("/api/auth/refresh");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_returnsTrueForActuatorPath() throws Exception {
        request.setServletPath("/actuator/health");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_returnsFalseForGeneralApiPath() throws Exception {
        request.setServletPath("/api/analyze");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void shouldNotFilter_returnsFalseForHistoryPath() throws Exception {
        request.setServletPath("/api/history");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    // ── valid cookie token sets authentication ───────────────────────────────────

    @Test
    void doFilterInternal_validCookieToken_setsAuthentication() throws Exception {
        request.setCookies(new Cookie("access_token", VALID_TOKEN));
        when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.extractEmail(VALID_TOKEN)).thenReturn(TEST_EMAIL);
        when(customUserDetailsService.loadUserByUsername(TEST_EMAIL)).thenReturn(mockUserDetails);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo(TEST_EMAIL);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").containsExactly("ROLE_ANALYST");
    }

    // ── valid Authorization header sets authentication ───────────────────────────

    @Test
    void doFilterInternal_validBearerHeader_setsAuthentication() throws Exception {
        request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
        when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.extractEmail(VALID_TOKEN)).thenReturn(TEST_EMAIL);
        when(customUserDetailsService.loadUserByUsername(TEST_EMAIL)).thenReturn(mockUserDetails);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo(TEST_EMAIL);
    }

    // ── cookie takes priority over header ────────────────────────────────────────

    @Test
    void doFilterInternal_cookieTakesPriorityOverHeader() throws Exception {
        String cookieToken = "cookie.token";
        String headerToken = "header.token";
        request.setCookies(new Cookie("access_token", cookieToken));
        request.addHeader("Authorization", "Bearer " + headerToken);
        when(jwtTokenProvider.validateToken(cookieToken)).thenReturn(true);
        when(jwtTokenProvider.extractEmail(cookieToken)).thenReturn(TEST_EMAIL);
        when(customUserDetailsService.loadUserByUsername(TEST_EMAIL)).thenReturn(mockUserDetails);

        filter.doFilter(request, response, filterChain);

        // Cookie token validated, not the header token
        verify(jwtTokenProvider).validateToken(cookieToken);
        verify(jwtTokenProvider, never()).validateToken(headerToken);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    // ── invalid token does not set authentication ────────────────────────────────

    @Test
    void doFilterInternal_expiredToken_doesNotSetAuthentication() throws Exception {
        request.setCookies(new Cookie("access_token", VALID_TOKEN));
        when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(customUserDetailsService, never()).loadUserByUsername(TEST_EMAIL);
    }

    @Test
    void doFilterInternal_malformedToken_doesNotSetAuthentication() throws Exception {
        request.setCookies(new Cookie("access_token", "not.a.valid.token"));
        when(jwtTokenProvider.validateToken("not.a.valid.token")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ── missing token does not set authentication ─────────────────────────────────

    @Test
    void doFilterInternal_noTokenPresent_doesNotSetAuthentication() throws Exception {
        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).validateToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void doFilterInternal_blankCookieValue_doesNotSetAuthentication() throws Exception {
        request.setCookies(new Cookie("access_token", "   "));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).validateToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void doFilterInternal_emptyCookieValue_doesNotSetAuthentication() throws Exception {
        request.setCookies(new Cookie("access_token", ""));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_bearerHeaderWithNoToken_doesNotSetAuthentication() throws Exception {
        request.addHeader("Authorization", "Bearer ");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_authorizationHeaderWithoutBearerPrefix_doesNotSetAuthentication() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ── user not found does not set authentication ────────────────────────────────

    @Test
    void doFilterInternal_userNotFound_doesNotSetAuthentication() throws Exception {
        request.setCookies(new Cookie("access_token", VALID_TOKEN));
        when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.extractEmail(VALID_TOKEN)).thenReturn(TEST_EMAIL);
        when(customUserDetailsService.loadUserByUsername(TEST_EMAIL))
                .thenThrow(new UsernameNotFoundException("User not found: " + TEST_EMAIL));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ── filter chain always continues ─────────────────────────────────────────────

    @Test
    void doFilterInternal_filterChainAlwaysContinues_onValidToken() throws Exception {
        request.setCookies(new Cookie("access_token", VALID_TOKEN));
        when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.extractEmail(VALID_TOKEN)).thenReturn(TEST_EMAIL);
        when(customUserDetailsService.loadUserByUsername(TEST_EMAIL)).thenReturn(mockUserDetails);

        var mockChain = org.mockito.Mockito.mock(FilterChain.class);
        filter.doFilter(request, response, mockChain);

        verify(mockChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_filterChainAlwaysContinues_onException() throws Exception {
        request.setCookies(new Cookie("access_token", VALID_TOKEN));
        when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenThrow(new RuntimeException("unexpected"));

        var mockChain = org.mockito.Mockito.mock(FilterChain.class);
        filter.doFilter(request, response, mockChain);

        verify(mockChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
