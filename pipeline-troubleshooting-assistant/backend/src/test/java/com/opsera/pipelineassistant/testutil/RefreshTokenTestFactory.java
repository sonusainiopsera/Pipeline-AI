package com.opsera.pipelineassistant.testutil;

import com.opsera.pipelineassistant.model.RefreshToken;
import com.opsera.pipelineassistant.model.User;

import java.time.LocalDateTime;

public final class RefreshTokenTestFactory {

    // 64-character hex strings simulating SHA-256 digests for use in tests.
    public static final String HASH_A = "a".repeat(64);
    public static final String HASH_B = "b".repeat(64);
    public static final String HASH_C = "c".repeat(64);
    public static final String HASH_D = "d".repeat(64);

    private RefreshTokenTestFactory() {}

    /** Valid token expiring 7 days from now. */
    public static RefreshToken valid(User user, String tokenHash) {
        return RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
    }

    /** Valid token using the default HASH_A constant. */
    public static RefreshToken valid(User user) {
        return valid(user, HASH_A);
    }

    /** Expired token whose expiresAt is 1 day in the past. */
    public static RefreshToken expired(User user, String tokenHash) {
        return RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    /** Expired token using the default HASH_B constant. */
    public static RefreshToken expired(User user) {
        return expired(user, HASH_B);
    }
}
