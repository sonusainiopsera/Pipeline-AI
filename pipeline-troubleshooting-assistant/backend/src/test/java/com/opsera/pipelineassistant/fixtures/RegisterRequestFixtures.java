package com.opsera.pipelineassistant.fixtures;

import com.opsera.pipelineassistant.dto.RegisterRequest;

/**
 * Factory methods for RegisterRequest test fixtures.
 */
public final class RegisterRequestFixtures {

    private RegisterRequestFixtures() {}

    public static RegisterRequest valid() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("user@example.com");
        req.setPassword("SecurePass123!");
        req.setDisplayName("Test User");
        return req;
    }

    public static RegisterRequest withEmail(String email) {
        RegisterRequest req = valid();
        req.setEmail(email);
        return req;
    }

    public static RegisterRequest withPassword(String password) {
        RegisterRequest req = valid();
        req.setPassword(password);
        return req;
    }

    public static RegisterRequest withDisplayName(String displayName) {
        RegisterRequest req = valid();
        req.setDisplayName(displayName);
        return req;
    }
}
