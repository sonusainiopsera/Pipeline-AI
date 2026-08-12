package com.opsera.pipelineassistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.pipelineassistant.audit.AuditService;
import com.opsera.pipelineassistant.model.RefreshToken;
import com.opsera.pipelineassistant.model.Role;
import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.repository.RefreshTokenRepository;
import com.opsera.pipelineassistant.repository.UserRepository;
import com.opsera.pipelineassistant.security.AesEncryptionUtil;
import com.opsera.pipelineassistant.security.JwtTokenProvider;
import com.opsera.pipelineassistant.security.MfaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceAuditTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private MfaService mfaService;
    @Mock private AesEncryptionUtil aesEncryptionUtil;
    @Mock private ObjectMapper objectMapper;
    @Mock private AuditService auditService;

    @InjectMocks
    private AuthService authService;

    private static final long REFRESH_EXPIRY = 86400L;
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "SecurePass123!";
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpirationSeconds", REFRESH_EXPIRY);
    }

    private User verifiedUser() {
        return User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .passwordHash("hashed")
                .displayName("Test User")
                .role(Role.ANALYST)
                .emailVerified(true)
                .build();
    }

    // ── LOGIN_SUCCESS ──────────────────────────────────────────────────────────

    @Test
    void login_success_logsLoginSuccess() {
        User user = verifiedUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, "hashed")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("raw-refresh");
        when(refreshTokenRepository.countByUserId(USER_ID)).thenReturn(0L);
        when(refreshTokenRepository.save(any())).thenReturn(null);
        when(userRepository.save(any())).thenReturn(user);

        authService.login(EMAIL, PASSWORD);

        verify(auditService).logEvent(
                eq(AuditService.LOGIN_SUCCESS),
                eq(AuditService.RESOURCE_USER),
                eq(USER_ID.toString()),
                any());
    }

    // ── LOGIN_FAILURE: unknown email ───────────────────────────────────────────

    @Test
    void login_unknownEmail_logsLoginFailure() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        try { authService.login(EMAIL, PASSWORD); } catch (Exception ignored) {}

        verify(auditService).logEvent(
                eq(AuditService.LOGIN_FAILURE),
                eq(AuditService.RESOURCE_USER),
                eq(EMAIL),
                any());
    }

    // ── LOGIN_FAILURE: wrong password ──────────────────────────────────────────

    @Test
    void login_wrongPassword_logsLoginFailure() {
        User user = verifiedUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, "hashed")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(user);

        try { authService.login(EMAIL, PASSWORD); } catch (Exception ignored) {}

        verify(auditService).logEvent(
                eq(AuditService.LOGIN_FAILURE),
                eq(AuditService.RESOURCE_USER),
                eq(USER_ID.toString()),
                any());
    }

    // ── MFA_VERIFY_SUCCESS ────────────────────────────────────────────────────

    @Test
    void verifyMfaChallenge_success_logsMfaVerifySuccess() throws Exception {
        User user = verifiedUser();
        String tokenHash = authService.hashToken("challenge-token");
        user.setMfaChallengeTokenHash(tokenHash);
        when(jwtTokenProvider.validateMfaChallengeToken("challenge-token")).thenReturn(true);
        when(jwtTokenProvider.extractMfaChallengeEmail("challenge-token")).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);
        when(aesEncryptionUtil.decrypt(any())).thenReturn("plain-secret");
        when(mfaService.verifyCode("plain-secret", "123456")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("raw-refresh");
        when(refreshTokenRepository.countByUserId(USER_ID)).thenReturn(0L);
        when(refreshTokenRepository.save(any())).thenReturn(null);

        authService.verifyMfaChallenge("challenge-token", "123456");

        verify(auditService).logEvent(
                eq(AuditService.MFA_VERIFY_SUCCESS),
                eq(AuditService.RESOURCE_USER),
                eq(USER_ID.toString()),
                any());
    }

    // ── MFA_VERIFY_FAILURE ────────────────────────────────────────────────────

    @Test
    void verifyMfaChallenge_wrongCode_logsMfaVerifyFailure() throws Exception {
        User user = verifiedUser();
        String tokenHash = authService.hashToken("challenge-token");
        user.setMfaChallengeTokenHash(tokenHash);
        when(jwtTokenProvider.validateMfaChallengeToken("challenge-token")).thenReturn(true);
        when(jwtTokenProvider.extractMfaChallengeEmail("challenge-token")).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);
        when(aesEncryptionUtil.decrypt(any())).thenReturn("plain-secret");
        when(mfaService.verifyCode("plain-secret", "000000")).thenReturn(false);

        try { authService.verifyMfaChallenge("challenge-token", "000000"); } catch (Exception ignored) {}

        verify(auditService).logEvent(
                eq(AuditService.MFA_VERIFY_FAILURE),
                eq(AuditService.RESOURCE_USER),
                eq(USER_ID.toString()),
                any());
    }

    // ── TOKEN_REFRESH ──────────────────────────────────────────────────────────

    @Test
    void refresh_success_logsTokenRefresh() {
        User user = verifiedUser();
        String rawToken = "raw-refresh-token";
        String hash = authService.hashToken(rawToken);
        RefreshToken stored = RefreshToken.builder()
                .user(user)
                .tokenHash(hash)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stored));
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("new-access");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("new-refresh");
        when(refreshTokenRepository.save(any())).thenReturn(null);

        authService.refresh(rawToken);

        verify(auditService).logEvent(
                eq(AuditService.TOKEN_REFRESH),
                eq(AuditService.RESOURCE_SESSION),
                eq(USER_ID.toString()),
                any());
    }

    // ── LOGOUT ────────────────────────────────────────────────────────────────

    @Test
    void logout_logsLogout() {
        authService.logout("raw-refresh-token");

        verify(auditService).logEvent(
                eq(AuditService.LOGOUT),
                eq(AuditService.RESOURCE_SESSION),
                isNull(),
                isNull());
    }

    // ── REGISTRATION ──────────────────────────────────────────────────────────

    @Test
    void register_success_logsRegistration() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        User saved = verifiedUser();
        when(userRepository.save(any())).thenReturn(saved);

        authService.register(EMAIL, "ValidPass123!", "Test User");

        verify(auditService).logEvent(
                eq(AuditService.REGISTRATION),
                eq(AuditService.RESOURCE_USER),
                eq(USER_ID.toString()),
                any());
    }

    // ── EMAIL_VERIFIED ────────────────────────────────────────────────────────

    @Test
    void verifyEmail_success_logsEmailVerified() {
        User user = User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .role(Role.ANALYST)
                .emailVerified(false)
                .verificationToken("token-abc")
                .verificationTokenExpiry(LocalDateTime.now().plusHours(1))
                .build();
        when(userRepository.findByVerificationToken("token-abc")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        authService.verifyEmail("token-abc");

        verify(auditService).logEvent(
                eq(AuditService.EMAIL_VERIFIED),
                eq(AuditService.RESOURCE_USER),
                eq(USER_ID.toString()),
                any());
    }
}
