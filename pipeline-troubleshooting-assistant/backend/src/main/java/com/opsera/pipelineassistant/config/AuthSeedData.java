package com.opsera.pipelineassistant.config;

import com.opsera.pipelineassistant.model.Role;
import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds verified local demo accounts when missing (dev profile only).
 * Password for all seeded users: DemoPass123!@#
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class AuthSeedData implements CommandLineRunner {

    public static final String DEMO_PASSWORD = "DemoPass123!@#";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedUser("manager@pipeline.local", "Demo Manager", Role.MANAGER);
        seedUser("admin@pipeline.local", "Demo KB Admin", Role.KB_ADMIN);
        seedUser("analyst@pipeline.local", "Demo Analyst", Role.ANALYST);
    }

    private void seedUser(String email, String displayName, Role role) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .displayName(displayName)
                .role(role)
                .emailVerified(true)
                .mfaEnabled(false)
                .build());
        log.info("[DEV] Seeded verified user '{}' with role {}", email, role);
    }
}
