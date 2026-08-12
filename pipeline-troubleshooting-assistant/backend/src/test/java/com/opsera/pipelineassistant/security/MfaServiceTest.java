package com.opsera.pipelineassistant.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.repository.UserRepository;
import com.opsera.pipelineassistant.testutil.MfaTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MfaServiceTest {

    @Mock
    private AesEncryptionUtil aesEncryptionUtil;

    @Mock
    private UserRepository userRepository;

    private MfaService mfaService;

    @BeforeEach
    void setUp() {
        mfaService = new MfaService(
                aesEncryptionUtil,
                new BCryptPasswordEncoder(4),
                userRepository,
                new ObjectMapper());
    }

    // ── generateSecret ────────────────────────────────────────────────────────

    @Test
    void generateSecret_returnsNonEmptyBase32String() {
        String secret = mfaService.generateSecret();

        assertThat(secret).isNotBlank();
        assertThat(secret).matches("[A-Z2-7]+=*");
    }

    @Test
    void generateSecret_eachCallProducesUniqueSecret() {
        String first = mfaService.generateSecret();
        String second = mfaService.generateSecret();

        assertThat(first).isNotEqualTo(second);
    }

    // ── buildQrCodeUri ────────────────────────────────────────────────────────

    @Test
    void buildQrCodeUri_startsWithOtpauthScheme() {
        String uri = mfaService.buildQrCodeUri("user@example.com", "JBSWY3DPEHPK3PXP");

        assertThat(uri).startsWith("otpauth://totp/");
    }

    @Test
    void buildQrCodeUri_containsEmailAndSecret() {
        String uri = mfaService.buildQrCodeUri("user@example.com", "TESTSECRET");

        assertThat(uri).contains("user@example.com");
        assertThat(uri).contains("TESTSECRET");
    }

    @Test
    void buildQrCodeUri_containsIssuerPipelineAssistant() {
        String uri = mfaService.buildQrCodeUri("user@example.com", "SECRET");

        assertThat(uri).contains("PipelineAssistant");
    }

    // ── verifyCode ────────────────────────────────────────────────────────────

    @Test
    void verifyCode_validCode_returnsTrue() throws Exception {
        String secret = mfaService.generateSecret();
        String validCode = MfaTestHelper.generateValidTotpCode(secret);

        assertThat(mfaService.verifyCode(secret, validCode)).isTrue();
    }

    @Test
    void verifyCode_invalidCode_returnsFalse() {
        String secret = mfaService.generateSecret();

        assertThat(mfaService.verifyCode(secret, "000000")).isFalse();
    }

    @Test
    void verifyCode_wrongLengthCode_returnsFalse() {
        String secret = mfaService.generateSecret();

        assertThat(mfaService.verifyCode(secret, "12345")).isFalse();
    }

    // ── generateRecoveryCodes ─────────────────────────────────────────────────

    @Test
    void generateRecoveryCodes_returnsExactly8Codes() {
        List<String> codes = mfaService.generateRecoveryCodes();

        assertThat(codes).hasSize(8);
    }

    @Test
    void generateRecoveryCodes_eachCodeIs8CharactersLong() {
        List<String> codes = mfaService.generateRecoveryCodes();

        codes.forEach(code -> assertThat(code).hasSize(8));
    }

    @Test
    void generateRecoveryCodes_codesAreAlphanumericUppercase() {
        List<String> codes = mfaService.generateRecoveryCodes();

        codes.forEach(code -> assertThat(code).matches("[A-Z0-9]{8}"));
    }

    @Test
    void generateRecoveryCodes_producesUniqueCodesWithinSet() {
        List<String> codes = mfaService.generateRecoveryCodes();

        assertThat(codes).doesNotHaveDuplicates();
    }

    // ── setupMfa ──────────────────────────────────────────────────────────────

    @Test
    void setupMfa_throwsBadRequest_whenMfaAlreadyEnabled() {
        User user = User.builder().email("user@example.com").mfaEnabled(true).build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> mfaService.setupMfa("user@example.com"));

        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        verify(userRepository, never()).save(any());
    }

    @Test
    void setupMfa_throwsNotFound_whenUserDoesNotExist() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> mfaService.setupMfa("nobody@example.com"));

        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void setupMfa_storesEncryptedSecretAndSetsExpiry() {
        User user = User.builder().email("user@example.com").mfaEnabled(false).build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(aesEncryptionUtil.encrypt(any())).thenReturn("encrypted-secret");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mfaService.setupMfa("user@example.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getMfaSecret()).isEqualTo("encrypted-secret");
        assertThat(saved.getMfaSetupExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void setupMfa_returnsQrCodeUriAndRecoveryCodes() {
        User user = User.builder().email("user@example.com").mfaEnabled(false).build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(aesEncryptionUtil.encrypt(any())).thenReturn("encrypted");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MfaService.MfaSetupData data = mfaService.setupMfa("user@example.com");

        assertThat(data.qrCodeUri()).startsWith("otpauth://totp/");
        assertThat(data.recoveryCodes()).hasSize(8);
    }

    // ── verifyMfa ─────────────────────────────────────────────────────────────

    @Test
    void verifyMfa_throwsBadRequest_whenSetupNotInitiated() {
        User user = User.builder().email("user@example.com").mfaSecret(null).mfaSetupExpiresAt(null).build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> mfaService.verifyMfa("user@example.com", "123456"));

        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void verifyMfa_throwsGone_whenSetupSessionExpired() {
        User user = User.builder()
                .email("user@example.com")
                .mfaSecret("encrypted")
                .mfaSetupExpiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> mfaService.verifyMfa("user@example.com", "123456"));

        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.GONE.value());
    }

    @Test
    void verifyMfa_throwsUnauthorized_whenCodeIsInvalid() {
        User user = User.builder()
                .email("user@example.com")
                .mfaSecret("encrypted")
                .mfaSetupExpiresAt(LocalDateTime.now().plusMinutes(5))
                .mfaEnabled(false)
                .build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(aesEncryptionUtil.decrypt("encrypted")).thenReturn(mfaService.generateSecret());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> mfaService.verifyMfa("user@example.com", "000000"));

        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void verifyMfa_setsMfaEnabledTrue_andClearsExpiry_onSuccess() throws Exception {
        String secret = mfaService.generateSecret();
        String validCode = MfaTestHelper.generateValidTotpCode(secret);

        User user = User.builder()
                .email("user@example.com")
                .mfaSecret("encrypted")
                .mfaSetupExpiresAt(LocalDateTime.now().plusMinutes(5))
                .mfaEnabled(false)
                .build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(aesEncryptionUtil.decrypt("encrypted")).thenReturn(secret);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mfaService.verifyMfa("user@example.com", validCode);

        assertThat(user.getMfaEnabled()).isTrue();
        assertThat(user.getMfaSetupExpiresAt()).isNull();
    }
}
