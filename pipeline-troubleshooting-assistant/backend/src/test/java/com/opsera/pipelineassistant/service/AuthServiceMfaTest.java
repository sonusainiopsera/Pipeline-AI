package com.opsera.pipelineassistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.pipelineassistant.dto.Responses.LoginResult;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceMfaTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private MfaService mfaService;
    @Mock private AesEncryptionUtil aesEncryptionUtil;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private AuthService authService;

    private static final long REFRESH_EXPIRY = 86400L;
    private static final String EMAIL = "mfa@example.com";
    private static final String PASSWORD = "SecurePass123!";
    private static final String HASH = "hashed-password";
    private static final String ENCRYPTED_SECRET = "AES_ENCRYPTED_SECRET==";
    private static final String PLAIN_SECRET = "JBSWY3DPEHPK3PXP";
    private static final String CHALLENGE_TOKEN = "challenge-jwt-token";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpirationSeconds", REFRESH_EXPIRY);
    }

    private User mfaUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email(EMAIL)
                .passwordHash(HASH)
                .displayName("MFA User")
                .emailVerified(true)
                .failedLoginAttempts(0)
                .role(Role.ANALYST)
                .mfaEnabled(true)
                .mfaSecret(ENCRYPTED_SECRET)
                .mfaChallengeTokenHash(authService.hashToken(CHALLENGE_TOKEN))
                .build();
    }

    private User nonMfaUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email(EMAIL)
                .passwordHash(HASH)
                .displayName("Non-MFA User")
                .emailVerified(true)
                .failedLoginAttempts(0)
                .role(Role.ANALYST)
                .mfaEnabled(false)
                .build();
    }

    // ── login() — MFA-enabled path ─────────────────────────────────────────────

    @Test
    void login_mfaEnabledUser_returnsMfaRequired() {
        User user = mfaUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtTokenProvider.generateMfaChallengeToken(EMAIL)).thenReturn(CHALLENGE_TOKEN);
        when(userRepository.save(any())).thenReturn(user);

        LoginResult result = authService.login(EMAIL, PASSWORD);

        assertThat(result.profile().mfaRequired()).isTrue();
        assertThat(result.challengeToken()).isEqualTo(CHALLENGE_TOKEN);
        assertThat(result.accessToken()).isNull();
        assertThat(result.rawRefreshToken()).isNull();
    }

    @Test
    void login_mfaEnabledUser_storesChallengeTokenHash() {
        User user = mfaUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtTokenProvider.generateMfaChallengeToken(EMAIL)).thenReturn(CHALLENGE_TOKEN);
        when(userRepository.save(any())).thenReturn(user);

        authService.login(EMAIL, PASSWORD);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getMfaChallengeTokenHash())
                .isEqualTo(authService.hashToken(CHALLENGE_TOKEN));
    }

    @Test
    void login_mfaEnabledUser_doesNotIssueRefreshToken() {
        User user = mfaUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtTokenProvider.generateMfaChallengeToken(EMAIL)).thenReturn(CHALLENGE_TOKEN);
        when(userRepository.save(any())).thenReturn(user);

        authService.login(EMAIL, PASSWORD);

        verify(refreshTokenRepository, never()).save(any());
        verify(jwtTokenProvider, never()).generateAccessToken(any());
    }

    // ── login() — non-MFA path ─────────────────────────────────────────────────

    @Test
    void login_nonMfaUser_returnsMfaRequiredFalseWithTokens() {
        User user = nonMfaUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("access-jwt");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("raw-refresh");
        when(refreshTokenRepository.countByUserId(user.getId())).thenReturn(0L);
        when(userRepository.save(any())).thenReturn(user);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoginResult result = authService.login(EMAIL, PASSWORD);

        assertThat(result.profile().mfaRequired()).isFalse();
        assertThat(result.accessToken()).isEqualTo("access-jwt");
        assertThat(result.rawRefreshToken()).isEqualTo("raw-refresh");
        assertThat(result.challengeToken()).isNull();
    }

    // ── verifyMfaChallenge() ───────────────────────────────────────────────────

    @Test
    void verifyMfaChallenge_validCode_issuesTokens() throws Exception {
        User user = mfaUser();
        when(jwtTokenProvider.validateMfaChallengeToken(CHALLENGE_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.extractMfaChallengeEmail(CHALLENGE_TOKEN)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(aesEncryptionUtil.decrypt(ENCRYPTED_SECRET)).thenReturn(PLAIN_SECRET);
        when(mfaService.verifyCode(PLAIN_SECRET, "123456")).thenReturn(true);
        when(userRepository.save(any())).thenReturn(user);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("access-jwt");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("raw-refresh");
        when(refreshTokenRepository.countByUserId(user.getId())).thenReturn(0L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoginResult result = authService.verifyMfaChallenge(CHALLENGE_TOKEN, "123456");

        assertThat(result.accessToken()).isEqualTo("access-jwt");
        assertThat(result.rawRefreshToken()).isEqualTo("raw-refresh");
        assertThat(result.profile().mfaRequired()).isFalse();
    }

    @Test
    void verifyMfaChallenge_invalidCode_returns401() {
        User user = mfaUser();
        when(jwtTokenProvider.validateMfaChallengeToken(CHALLENGE_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.extractMfaChallengeEmail(CHALLENGE_TOKEN)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(aesEncryptionUtil.decrypt(ENCRYPTED_SECRET)).thenReturn(PLAIN_SECRET);
        when(mfaService.verifyCode(PLAIN_SECRET, "000000")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(user);

        assertThatThrownBy(() -> authService.verifyMfaChallenge(CHALLENGE_TOKEN, "000000"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void verifyMfaChallenge_expiredOrInvalidToken_returns401() {
        when(jwtTokenProvider.validateMfaChallengeToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.verifyMfaChallenge("bad-token", "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void verifyMfaChallenge_alreadyUsedToken_returns401() {
        User user = mfaUser();
        user.setMfaChallengeTokenHash("different-hash"); // hash doesn't match
        when(jwtTokenProvider.validateMfaChallengeToken(CHALLENGE_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.extractMfaChallengeEmail(CHALLENGE_TOKEN)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.verifyMfaChallenge(CHALLENGE_TOKEN, "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void verifyMfaChallenge_clearsTokenHashBeforeVerifying() {
        User user = mfaUser();
        when(jwtTokenProvider.validateMfaChallengeToken(CHALLENGE_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.extractMfaChallengeEmail(CHALLENGE_TOKEN)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(aesEncryptionUtil.decrypt(ENCRYPTED_SECRET)).thenReturn(PLAIN_SECRET);
        when(mfaService.verifyCode(PLAIN_SECRET, "000000")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(user);

        assertThatThrownBy(() -> authService.verifyMfaChallenge(CHALLENGE_TOKEN, "000000"))
                .isInstanceOf(ResponseStatusException.class);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getMfaChallengeTokenHash()).isNull();
    }

    // ── verifyMfaRecovery() ────────────────────────────────────────────────────

    @Test
    void verifyMfaRecovery_validCode_issuesTokensAndMarksUsed() throws Exception {
        User user = mfaUser();
        String hashedCode = "$2a$12$mockedBcryptHashForTestRecovery";
        List<String> codeHashes = List.of(hashedCode, "$2a$12$otherHash");
        user.setRecoveryCodes("[\"" + hashedCode + "\",\"$2a$12$otherHash\"]");

        when(jwtTokenProvider.validateMfaChallengeToken(CHALLENGE_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.extractMfaChallengeEmail(CHALLENGE_TOKEN)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(new java.util.ArrayList<>(codeHashes));
        when(passwordEncoder.matches("ABCD1234", hashedCode)).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("[null,\"$2a$12$otherHash\"]");
        when(userRepository.save(any())).thenReturn(user);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("access-jwt");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("raw-refresh");
        when(refreshTokenRepository.countByUserId(user.getId())).thenReturn(0L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoginResult result = authService.verifyMfaRecovery(CHALLENGE_TOKEN, "ABCD1234");

        assertThat(result.accessToken()).isEqualTo("access-jwt");
    }

    @Test
    void verifyMfaRecovery_invalidCode_returns401() throws Exception {
        User user = mfaUser();
        user.setRecoveryCodes("[\"$2a$12$hash1\",\"$2a$12$hash2\"]");

        when(jwtTokenProvider.validateMfaChallengeToken(CHALLENGE_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.extractMfaChallengeEmail(CHALLENGE_TOKEN)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(new java.util.ArrayList<>(List.of("$2a$12$hash1", "$2a$12$hash2")));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(userRepository.save(any())).thenReturn(user);

        assertThatThrownBy(() -> authService.verifyMfaRecovery(CHALLENGE_TOKEN, "WRONGCODE"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void verifyMfaRecovery_allCodesNull_returns423() throws Exception {
        User user = mfaUser();
        user.setRecoveryCodes("[null,null]");

        when(jwtTokenProvider.validateMfaChallengeToken(CHALLENGE_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.extractMfaChallengeEmail(CHALLENGE_TOKEN)).thenReturn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(new java.util.ArrayList<>(java.util.Arrays.asList(null, null)));
        when(passwordEncoder.matches(anyString(), any())).thenReturn(false);
        when(userRepository.save(any())).thenReturn(user);

        assertThatThrownBy(() -> authService.verifyMfaRecovery(CHALLENGE_TOKEN, "ABCD1234"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void verifyMfaRecovery_expiredToken_returns401() {
        when(jwtTokenProvider.validateMfaChallengeToken("expired-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.verifyMfaRecovery("expired-token", "ABCD1234"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
