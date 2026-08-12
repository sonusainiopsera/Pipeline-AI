package com.opsera.pipelineassistant;

import com.opsera.pipelineassistant.analysis.ResponseProperties;
import com.opsera.pipelineassistant.analysis.ScoringProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ScoringProperties.class, ResponseProperties.class})
public class PipelineAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(PipelineAssistantApplication.class, args);
    }
}
