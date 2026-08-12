package com.opsera.pipelineassistant.testutil;

import com.opsera.pipelineassistant.model.Role;
import com.opsera.pipelineassistant.model.User;

public final class UserTestFactory {

    private UserTestFactory() {}

    public static User analyst() {
        return User.builder()
                .email("analyst@example.com")
                .passwordHash("$2a$12$mockHashForTestingAnalyst")
                .displayName("Test Analyst")
                .role(Role.ANALYST)
                .build();
    }

    public static User kbAdmin() {
        return User.builder()
                .email("kbadmin@example.com")
                .passwordHash("$2a$12$mockHashForTestingKbAdmin")
                .displayName("Test KB Admin")
                .role(Role.KB_ADMIN)
                .build();
    }

    public static User manager() {
        return User.builder()
                .email("manager@example.com")
                .passwordHash("$2a$12$mockHashForTestingManager")
                .displayName("Test Manager")
                .role(Role.MANAGER)
                .build();
    }

    public static User withEmail(String email) {
        return User.builder()
                .email(email)
                .passwordHash("$2a$12$mockHashForTestingDefault")
                .displayName("Test User")
                .build();
    }

    public static User withMfa(String email) {
        return User.builder()
                .email(email)
                .passwordHash("$2a$12$mockHashForTestingMfa")
                .displayName("MFA User")
                .mfaSecret("AES256_ENCRYPTED_TOTP_SECRET==")
                .mfaEnabled(true)
                .build();
    }

    public static User lockedOut(String email) {
        return User.builder()
                .email(email)
                .passwordHash("$2a$12$mockHashForTestingLocked")
                .displayName("Locked User")
                .failedLoginAttempts(5)
                .build();
    }
}
