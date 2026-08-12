package com.opsera.pipelineassistant.testutil;

import dev.samstevens.totp.code.CodeGenerationException;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.time.SystemTimeProvider;

/**
 * Test utility for generating valid TOTP codes from a known secret.
 * Use this in integration and service tests that call /mfa/verify
 * to simulate a user scanning the QR code and providing the code
 * produced by their authenticator app.
 */
public final class MfaTestHelper {

    private MfaTestHelper() {}

    /**
     * Generates the current 6-digit TOTP code for the given base32 secret
     * using SHA-1 with a 30-second period, matching the parameters set in
     * {@code MfaService.buildQrCodeUri}.
     */
    public static String generateValidTotpCode(String secret) throws CodeGenerationException {
        DefaultCodeGenerator generator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
        SystemTimeProvider timeProvider = new SystemTimeProvider();
        long counter = Math.floorDiv(timeProvider.getTime(), 30);
        return generator.generate(secret, counter);
    }
}
