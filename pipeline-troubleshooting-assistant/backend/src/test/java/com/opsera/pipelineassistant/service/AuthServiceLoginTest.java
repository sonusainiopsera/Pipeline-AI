package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.dto.Responses.LoginResult;
import com.opsera.pipelineassistant.exception.AccountLockedException;
import com.opsera.pipelineassistant.exception.EmailNotVerifiedException;
import com.opsera.pipelineassistant.model.RefreshToken;
import com.opsera.pipelineassistant.model.Role;
import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.repository.RefreshTokenRepository;
import com.opsera.pipelineassistant.repository.UserRepository;
import com.opsera.pipelineassistant.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private AuthService authService;

    private static final long REFRESH_EXPIRY = 86400L;
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "SecurePass123!";
    private static final String HASH = "hashed-password";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpirationSeconds", REFRESH_EXPIRY);
    }

    private User verifiedUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email(EMAIL)
                .passwordHash(HASH)
                .displayName("Test User")
                .emailVerified(true)
                .failedLoginAttempts(0)
                .role(Role.ANALYST)
                .build();
    }

    // ── Success ───────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsLoginResult() {
        User user = verifiedUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("access-jwt");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("raw-refresh");
        when(refreshTokenRepository.countByUserId(user.getId())).thenReturn(0L);
        when(userRepository.save(any())).thenReturn(user);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoginResult result = authService.login(EMAIL, PASSWORD);

        assertThat(result.accessToken()).isEqualTo("access-jwt");
        assertThat(result.rawRefreshToken()).isEqualTo("raw-refresh");
        assertThat(result.profile().email()).isEqualTo(EMAIL);
        assertThat(result.profile().displayName()).isEqualTo("Test User");
        assertThat(result.profile().role()).isEqualTo("ANALYST");
    }

    @Test
    void login_success_storesRefreshTokenHash() {
        User user = verifiedUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("access-jwt");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("raw-refresh");
        when(refreshTokenRepository.countByUserId(user.getId())).thenReturn(0L);
        when(userRepository.save(any())).thenReturn(user);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.login(EMAIL, PASSWORD);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        String expectedHash = authService.hashToken("raw-refresh");
        assertThat(captor.getValue().getTokenHash()).isEqualTo(expectedHash);
        assertThat(captor.getValue().getExpiresAt()).isAfter(LocalDateTime.now().plusSeconds(REFRESH_EXPIRY - 2));
    }

    @Test
    void login_success_resetsFailedAttempts() {
        User user = verifiedUser();
        user.setFailedLoginAttempts(3);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("jwt");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh");
        when(refreshTokenRepository.countByUserId(user.getId())).thenReturn(0L);
        when(userRepository.save(any())).thenReturn(user);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.login(EMAIL, PASSWORD);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isEqualTo(0);
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    @Test
    void login_normalizesEmailToLowercase() {
        User user = verifiedUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("jwt");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh");
        when(refreshTokenRepository.countByUserId(user.getId())).thenReturn(0L);
        when(userRepository.save(any())).thenReturn(user);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.login("  USER@EXAMPLE.COM  ", PASSWORD);

        verify(userRepository).findByEmail(EMAIL);
    }

    // ── Unknown email ─────────────────────────────────────────────────────────

    @Test
    void login_unknownEmail_throws401WithGenericMessage() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("unknown@example.com", PASSWORD))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException ex = (ResponseStatusException) e;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getReason()).isEqualTo("Invalid email or password");
                });
    }

    // ── Unverified email ──────────────────────────────────────────────────────

    @Test
    void login_unverifiedEmail_throwsEmailNotVerifiedException() {
        User user = verifiedUser();
        user.setEmailVerified(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(EMAIL, PASSWORD))
                .isInstanceOf(EmailNotVerifiedException.class)
                .hasMessageContaining("verify your email");
    }

    // ── Locked account ────────────────────────────────────────────────────────

    @Test
    void login_lockedAccount_throwsAccountLockedException() {
        User user = verifiedUser();
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(EMAIL, PASSWORD))
                .isInstanceOf(AccountLockedException.class)
                .hasMessageContaining("locked");
    }

    @Test
    void login_expiredLock_clearsLockAndAllowsLogin() {
        User user = verifiedUser();
        user.setLockedUntil(LocalDateTime.now().minusSeconds(1));
        user.setFailedLoginAttempts(5);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("jwt");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh");
        when(refreshTokenRepository.countByUserId(user.getId())).thenReturn(0L);
        when(userRepository.save(any())).thenReturn(user);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoginResult result = authService.login(EMAIL, PASSWORD);

        assertThat(result.profile().email()).isEqualTo(EMAIL);
    }

    // ── Brute-force protection ─────────────────────────────────────────────────

    @Test
    void login_wrongPassword_incrementsFailedAttempts() {
        User user = verifiedUser();
        user.setFailedLoginAttempts(1);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(EMAIL, PASSWORD))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isEqualTo(2);
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    @Test
    void login_fifthWrongPassword_locksAccount() {
        User user = verifiedUser();
        user.setFailedLoginAttempts(4);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(EMAIL, PASSWORD))
                .isInstanceOf(ResponseStatusException.class);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isEqualTo(5);
        assertThat(captor.getValue().getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(14));
    }

    // ── Session limit ─────────────────────────────────────────────────────────

    @Test
    void login_sessionLimitReached_deletesOldestToken() {
        User user = verifiedUser();
        RefreshToken oldest = RefreshToken.builder()
                .user(user)
                .tokenHash("old-hash")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("jwt");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh");
        when(refreshTokenRepository.countByUserId(user.getId())).thenReturn(3L);
        when(refreshTokenRepository.findFirstByUserIdOrderByCreatedAtAsc(user.getId()))
                .thenReturn(Optional.of(oldest));
        when(userRepository.save(any())).thenReturn(user);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.login(EMAIL, PASSWORD);

        verify(refreshTokenRepository).deleteByTokenHash("old-hash");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_belowSessionLimit_doesNotDeleteAnyToken() {
        User user = verifiedUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("jwt");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh");
        when(refreshTokenRepository.countByUserId(user.getId())).thenReturn(2L);
        when(userRepository.save(any())).thenReturn(user);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.login(EMAIL, PASSWORD);

        verify(refreshTokenRepository, never()).findFirstByUserIdOrderByCreatedAtAsc(any());
        verify(refreshTokenRepository, never()).deleteByTokenHash(anyString());
    }
}
