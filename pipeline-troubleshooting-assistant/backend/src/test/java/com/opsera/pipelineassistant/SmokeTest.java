package com.opsera.pipelineassistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test that verifies the Spring application context loads successfully.
 * Uses the "test" profile which configures an H2 in-memory database in place of PostgreSQL.
 */
@SpringBootTest
@ActiveProfiles("test")
class SmokeTest {

    @Test
    void contextLoads() {
        // Verifies that the Spring application context starts without errors.
        // The test profile (application-test.yml) provides an H2 in-memory datasource
        // so no external PostgreSQL instance is required.
    }
}
