package com.opsera.pipelineassistant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Method-level {@code @PreAuthorize} rules apply only when auth is enforced.
 * Under {@code auth-optional}, filter-chain permitAll already opens the API for
 * local/transitional use, so method security stays off to avoid blanket 403s.
 */
@Configuration
@Profile("!auth-optional")
@EnableMethodSecurity
public class MethodSecurityConfig {
}
