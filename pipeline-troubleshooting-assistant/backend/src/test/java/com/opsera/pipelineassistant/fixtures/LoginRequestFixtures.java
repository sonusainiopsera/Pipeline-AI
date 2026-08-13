package com.opsera.pipelineassistant.fixtures;

import com.opsera.pipelineassistant.dto.LoginRequest;

public final class LoginRequestFixtures {

    private LoginRequestFixtures() {}

    public static LoginRequest valid() {
        LoginRequest req = new LoginRequest();
        req.setEmail("user@example.com");
        req.setPassword("SecurePass123!");
        return req;
    }

    public static LoginRequest withEmail(String email) {
        LoginRequest req = valid();
        req.setEmail(email);
        return req;
    }

    public static LoginRequest withPassword(String password) {
        LoginRequest req = valid();
        req.setPassword(password);
        return req;
    }
}
