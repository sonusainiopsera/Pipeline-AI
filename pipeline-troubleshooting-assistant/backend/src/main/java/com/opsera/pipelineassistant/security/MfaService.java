package com.opsera.pipelineassistant.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.repository.UserRepository;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MfaService {

    private static final String ISSUER = "PipelineAssistant";
    private static final int RECOVERY_CODE_COUNT = 8;
    private static final int RECOVERY_CODE_LENGTH = 8;
    private static final int SETUP_TIMEOUT_MINUTES = 10;
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final AesEncryptionUtil aesEncryptionUtil;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();

    public String generateSecret() {
        return secretGenerator.generate();
    }

    public String buildQrCodeUri(String email, String secret) {
        QrData data = new QrData.Builder()
                .label(email)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        return data.getUri();
    }

    public boolean verifyCode(String secret, String code) {
        try {
            CodeGenerator codeGenerator = new DefaultCodeGenerator();
            DefaultCodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, new SystemTimeProvider());
            verifier.setAllowedTimePeriodDiscrepancy(1);
            return verifier.isValidCode(secret, code);
        } catch (Exception e) {
            log.warn("TOTP verification error: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    public List<String> generateRecoveryCodes() {
        SecureRandom random = new SecureRandom();
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            StringBuilder sb = new StringBuilder(RECOVERY_CODE_LENGTH);
            for (int j = 0; j < RECOVERY_CODE_LENGTH; j++) {
                sb.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    @Transactional
    public MfaSetupData setupMfa(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "MFA is already enabled for this account");
        }

        String secret = generateSecret();
        String qrCodeUri = buildQrCodeUri(email, secret);
        String encryptedSecret = aesEncryptionUtil.encrypt(secret);

        List<String> plainCodes = generateRecoveryCodes();
        List<String> hashedCodes = new ArrayList<>();
        for (String code : plainCodes) {
            hashedCodes.add(passwordEncoder.encode(code));
        }

        try {
            user.setMfaSecret(encryptedSecret);
            user.setRecoveryCodes(objectMapper.writeValueAsString(hashedCodes));
            user.setMfaSetupExpiresAt(LocalDateTime.now().plusMinutes(SETUP_TIMEOUT_MINUTES));
            userRepository.save(user);
        } catch (Exception e) {
            log.error("MFA setup storage failed for '{}': {}", email, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "MFA setup failed");
        }

        log.info("MFA setup initiated for '{}'", email);
        return new MfaSetupData(qrCodeUri, plainCodes);
    }

    @Transactional
    public void verifyMfa(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getMfaSecret() == null || user.getMfaSetupExpiresAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "MFA setup not initiated. Please call /mfa/setup first.");
        }

        if (user.getMfaSetupExpiresAt().isBefore(LocalDateTime.now())) {
            user.setMfaSecret(null);
            user.setMfaSetupExpiresAt(null);
            userRepository.save(user);
            throw new ResponseStatusException(HttpStatus.GONE,
                    "MFA setup session has expired. Please start over.");
        }

        String decryptedSecret;
        try {
            decryptedSecret = aesEncryptionUtil.decrypt(user.getMfaSecret());
        } catch (Exception e) {
            log.error("Failed to decrypt MFA secret for '{}'", email);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "MFA verification failed");
        }

        if (!verifyCode(decryptedSecret, code)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid TOTP code");
        }

        user.setMfaEnabled(true);
        user.setMfaSetupExpiresAt(null);
        userRepository.save(user);
        log.info("MFA enrollment completed for '{}'", email);
    }

    public record MfaSetupData(String qrCodeUri, List<String> recoveryCodes) {}
}
