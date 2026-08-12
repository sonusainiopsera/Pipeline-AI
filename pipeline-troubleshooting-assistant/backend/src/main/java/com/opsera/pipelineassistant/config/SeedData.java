package com.opsera.pipelineassistant.config;

import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import com.opsera.pipelineassistant.repository.ErrorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeedData implements CommandLineRunner {

    private final ErrorRepository errorRepository;

    @Override
    public void run(String... args) {
        if (errorRepository.count() == 0) {
            errorRepository.save(ErrorKnowledgeBase.builder()
                .errorPattern("OutOfMemoryError, heap space, java.lang.OutOfMemoryError")
                .category("Memory")
                .rootCause("Java heap space exhausted during pipeline execution")
                .solution("Increase JVM heap size using -Xmx flag or optimize memory usage in the build")
                .severity("HIGH")
                .build());

            errorRepository.save(ErrorKnowledgeBase.builder()
                .errorPattern("connection refused, ECONNREFUSED, connection timeout")
                .category("Network")
                .rootCause("Service or dependency is unreachable during pipeline execution")
                .solution("Verify network connectivity and ensure all required services are running")
                .severity("HIGH")
                .build());

            errorRepository.save(ErrorKnowledgeBase.builder()
                .errorPattern("permission denied, access denied, EACCES, unauthorized")
                .category("Permissions")
                .rootCause("Insufficient permissions to access a resource or execute an operation")
                .solution("Review and update file/directory permissions or IAM roles")
                .severity("MEDIUM")
                .build());

            errorRepository.save(ErrorKnowledgeBase.builder()
                .errorPattern("npm ERR, node_modules, package.json, dependency resolution")
                .category("Dependency")
                .rootCause("Node.js package installation or dependency resolution failed")
                .solution("Clear npm cache and delete node_modules, then reinstall dependencies")
                .severity("MEDIUM")
                .build());

            errorRepository.save(ErrorKnowledgeBase.builder()
                .errorPattern("docker build failed, Dockerfile, image pull, container")
                .category("Docker")
                .rootCause("Docker image build or container operation failed during pipeline")
                .solution("Review Dockerfile for errors and ensure base images are accessible")
                .severity("HIGH")
                .build());

            errorRepository.save(ErrorKnowledgeBase.builder()
                .errorPattern("test failed, assertion error, junit, test suite")
                .category("Testing")
                .rootCause("One or more automated tests failed during the pipeline execution")
                .solution("Review test output, fix failing tests, and ensure test environment is properly configured")
                .severity("MEDIUM")
                .build());
        }
    }
}
