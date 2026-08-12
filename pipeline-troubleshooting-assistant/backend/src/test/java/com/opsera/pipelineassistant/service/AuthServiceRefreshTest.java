package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.dto.Responses.RefreshResult;
import com.opsera.pipelineassistant.model.RefreshToken;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private static final String RAW_TOKEN = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899";
    private static final long REFRESH_EXPIRY_SECONDS = 86400L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpirationSeconds", REFRESH_EXPIRY_SECONDS);
        testUser = User.builder()
                .email("user@example.com")
                .passwordHash("hashed")
                .displayName("Test")
                .emailVerified(true)
                .build();
    }

    private RefreshToken validStoredToken() {
        String hash = authService.hashToken(RAW_TOKEN);
        return RefreshToken.builder()
                .user(testUser)
                .tokenHash(hash)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
    }

    // ── Refresh: success ──────────────────────────────────────────────────────

    @Test
    void refresh_validToken_returnsNewTokens() {
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(validStoredToken()));
        when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("new-access-jwt");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("new-raw-refresh");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshResult result = authService.refresh(RAW_TOKEN);

        assertThat(result.accessToken()).isEqualTo("new-access-jwt");
        assertThat(result.refreshToken()).isEqualTo("new-raw-refresh");
    }

    @Test
    void refresh_rotatesToken_deletesOldSavesNew() {
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(validStoredToken()));
        when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("jwt");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("new-raw-refresh");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String oldHash = authService.hashToken(RAW_TOKEN);
        authService.refresh(RAW_TOKEN);

        verify(refreshTokenRepository).deleteByTokenHash(oldHash);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(authService.hashToken("new-raw-refresh"));
        assertThat(saved.getValue().getExpiresAt()).isAfter(LocalDateTime.now().plusSeconds(REFRESH_EXPIRY_SECONDS - 2));
    }

    // ── Refresh: invalid/expired ──────────────────────────────────────────────

    @Test
    void refresh_nullToken_throws401() {
        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void refresh_blankToken_throws401() {
        assertThatThrownBy(() -> authService.refresh("   "))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void refresh_unknownTokenHash_throws401() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(RAW_TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void refresh_expiredToken_deletesTokenAndThrows401() {
        RefreshToken expired = RefreshToken.builder()
                .user(testUser)
                .tokenHash(authService.hashToken(RAW_TOKEN))
                .expiresAt(LocalDateTime.now().minusSeconds(1))
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(RAW_TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(refreshTokenRepository).deleteByTokenHash(authService.hashToken(RAW_TOKEN));
        verify(refreshTokenRepository, never()).save(any());
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_validToken_deletesFromRepository() {
        assertThatCode(() -> authService.logout(RAW_TOKEN)).doesNotThrowAnyException();
        verify(refreshTokenRepository).deleteByTokenHash(authService.hashToken(RAW_TOKEN));
    }

    @Test
    void logout_nullToken_noOp() {
        assertThatCode(() -> authService.logout(null)).doesNotThrowAnyException();
        verify(refreshTokenRepository, never()).deleteByTokenHash(any());
    }

    @Test
    void logout_blankToken_noOp() {
        assertThatCode(() -> authService.logout("")).doesNotThrowAnyException();
        verify(refreshTokenRepository, never()).deleteByTokenHash(any());
    }

    @Test
    void logout_repositoryThrows_doesNotPropagate() {
        org.mockito.Mockito.doThrow(new RuntimeException("DB error"))
                .when(refreshTokenRepository).deleteByTokenHash(anyString());

        assertThatCode(() -> authService.logout(RAW_TOKEN)).doesNotThrowAnyException();
    }

    // ── hashToken ─────────────────────────────────────────────────────────────

    @Test
    void hashToken_deterministicFor64HexChars() {
        String h1 = authService.hashToken(RAW_TOKEN);
        String h2 = authService.hashToken(RAW_TOKEN);
        assertThat(h1).isEqualTo(h2).hasSize(64);
    }

    @Test
    void hashToken_differentInputsProduceDifferentHashes() {
        assertThat(authService.hashToken("tokenA")).isNotEqualTo(authService.hashToken("tokenB"));
    }
}
