package com.opsera.pipelineassistant.service;

public interface EmailService {

    /**
     * Sends an email verification link to the given address.
     *
     * @param email the recipient's email address
     * @param token the opaque verification token to include in the link
     */
    void sendVerificationEmail(String email, String token);
}
