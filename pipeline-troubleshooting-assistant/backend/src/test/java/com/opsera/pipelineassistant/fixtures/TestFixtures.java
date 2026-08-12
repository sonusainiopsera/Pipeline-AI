package com.opsera.pipelineassistant.fixtures;

import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;

import java.util.List;

/**
 * Shared factory for ErrorKnowledgeBase test data.
 *
 * The 6 entries mirror SeedData.java exactly so tests can reproduce the same
 * match scores that the running application produces against real log text.
 */
public final class TestFixtures {

    private TestFixtures() {}

    // ── Individual seed-data entries ──────────────────────────────────────────

    public static ErrorKnowledgeBase memoryEntry() {
        return ErrorKnowledgeBase.builder()
                .id(1L)
                .errorPattern("OutOfMemoryError, heap space, java.lang.OutOfMemoryError")
                .category("Memory")
                .rootCause("Java heap space exhausted during pipeline execution")
                .solution("Increase JVM heap size using -Xmx flag or optimize memory usage in the build")
                .severity("HIGH")
                .build();
    }

    public static ErrorKnowledgeBase networkEntry() {
        return ErrorKnowledgeBase.builder()
                .id(2L)
                .errorPattern("connection refused, ECONNREFUSED, connection timeout")
                .category("Network")
                .rootCause("Service or dependency is unreachable during pipeline execution")
                .solution("Verify network connectivity and ensure all required services are running")
                .severity("HIGH")
                .build();
    }

    public static ErrorKnowledgeBase permissionsEntry() {
        return ErrorKnowledgeBase.builder()
                .id(3L)
                .errorPattern("permission denied, access denied, EACCES, unauthorized")
                .category("Permissions")
                .rootCause("Insufficient permissions to access a resource or execute an operation")
                .solution("Review and update file/directory permissions or IAM roles")
                .severity("MEDIUM")
                .build();
    }

    public static ErrorKnowledgeBase dependencyEntry() {
        return ErrorKnowledgeBase.builder()
                .id(4L)
                .errorPattern("npm ERR, node_modules, package.json, dependency resolution")
                .category("Dependency")
                .rootCause("Node.js package installation or dependency resolution failed")
                .solution("Clear npm cache and delete node_modules, then reinstall dependencies")
                .severity("MEDIUM")
                .build();
    }

    public static ErrorKnowledgeBase dockerEntry() {
        return ErrorKnowledgeBase.builder()
                .id(5L)
                .errorPattern("docker build failed, Dockerfile, image pull, container")
                .category("Docker")
                .rootCause("Docker image build or container operation failed during pipeline")
                .solution("Review Dockerfile for errors and ensure base images are accessible")
                .severity("HIGH")
                .build();
    }

    public static ErrorKnowledgeBase testingEntry() {
        return ErrorKnowledgeBase.builder()
                .id(6L)
                .errorPattern("test failed, assertion error, junit, test suite")
                .category("Testing")
                .rootCause("One or more automated tests failed during the pipeline execution")
                .solution("Review test output, fix failing tests, and ensure test environment is properly configured")
                .severity("MEDIUM")
                .build();
    }

    /** All 6 seed entries in the same order SeedData.java saves them. */
    public static List<ErrorKnowledgeBase> allSeedEntries() {
        return List.of(
                memoryEntry(),
                networkEntry(),
                permissionsEntry(),
                dependencyEntry(),
                dockerEntry(),
                testingEntry()
        );
    }

    // ── Representative log text samples ───────────────────────────────────────

    /** Log text that matches all 3 keywords in memoryEntry (score = 3). */
    public static String memoryLog() {
        return "java.lang.OutOfMemoryError: Java heap space exhausted in build step";
    }

    /** Log text that matches all 3 keywords in networkEntry (score = 3). */
    public static String networkLog() {
        return "connection refused: ECONNREFUSED — connection timeout after 30s";
    }

    /** Log text that matches all 4 keywords in permissionsEntry (score = 4). */
    public static String permissionsLog() {
        return "permission denied: EACCES on /var/run — access denied, unauthorized operation";
    }

    /** Log text that matches all 4 keywords in dependencyEntry (score = 4). */
    public static String dependencyLog() {
        return "npm ERR! node_modules/react package.json dependency resolution failed";
    }

    /** Log text that matches all 4 keywords in dockerEntry (score = 4). */
    public static String dockerLog() {
        return "docker build failed in Dockerfile step, image pull error, container exited";
    }

    /** Log text that matches all 4 keywords in testingEntry (score = 4). */
    public static String testingLog() {
        return "test failed: assertion error in junit test suite execution";
    }
}
