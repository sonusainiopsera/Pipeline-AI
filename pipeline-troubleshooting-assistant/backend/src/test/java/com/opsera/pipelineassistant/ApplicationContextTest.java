package com.opsera.pipelineassistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Spring application context loads successfully with Flyway
 * on the classpath. Uses the "test" profile (H2 in-memory database) so no
 * external PostgreSQL instance is required.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void applicationContextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void flywayBeanIsPresent() {
        assertThat(applicationContext.containsBean("flywayInitializer")
                || applicationContext.containsBean("flyway")).isTrue();
    }
}
