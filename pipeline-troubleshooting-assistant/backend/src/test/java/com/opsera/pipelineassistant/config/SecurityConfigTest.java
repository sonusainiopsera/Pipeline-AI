package com.opsera.pipelineassistant.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    void securityFilterChainBeanLoads() {
        assertThat(securityFilterChain).isNotNull();
    }

    @Test
    void securityConfigBeanIsPresent() {
        assertThat(securityFilterChain).isInstanceOf(SecurityFilterChain.class);
    }
}
