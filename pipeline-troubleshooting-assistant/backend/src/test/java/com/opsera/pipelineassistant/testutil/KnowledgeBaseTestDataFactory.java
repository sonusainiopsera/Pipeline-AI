package com.opsera.pipelineassistant.testutil;

import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory that generates realistic ErrorKnowledgeBase test data at scale.
 * All entries are deterministic (no random state) for test reproducibility.
 * Covers 10 categories with distinct error patterns, root causes, and severities.
 */
public final class KnowledgeBaseTestDataFactory {

    private KnowledgeBaseTestDataFactory() {}

    private static final String[][] CATEGORY_TEMPLATES = {
        // { category, severity, pattern-prefix, root-cause-prefix, solution-prefix }
        { "Authentication", "HIGH",
            "login failed, invalid credentials, authentication error, token expired, unauthorized access, password incorrect, session timeout",
            "User credentials are invalid or the authentication token has expired",
            "Reset the user credentials and ensure the token refresh mechanism is functioning" },
        { "Docker", "HIGH",
            "container failed, image not found, docker daemon, port conflict, volume mount, network error, container exit",
            "Docker container failed to start due to missing image or configuration error",
            "Verify the Docker image tag and ensure the daemon is running" },
        { "Network", "MEDIUM",
            "connection refused, request timeout, DNS resolution, firewall blocked, SSL certificate, network unreachable, packet loss",
            "Network connectivity failure caused by firewall rules or DNS misconfiguration",
            "Review network policies, firewall rules, and DNS configuration" },
        { "Memory", "CRITICAL",
            "OutOfMemoryError, heap space, GC overhead, memory leak, OOM killer, swap exhausted, memory limit",
            "JVM heap space exhausted due to memory leak or under-provisioned heap settings",
            "Increase JVM heap size and investigate memory leak with heap dump analysis" },
        { "Build", "MEDIUM",
            "compile error, dependency missing, build failed, Maven error, classpath conflict, JAR not found, Gradle error",
            "Build failure caused by missing dependency or classpath conflict between artifact versions",
            "Check dependency versions and run mvn dependency:tree to identify conflicts" },
        { "Deployment", "HIGH",
            "rollout failed, pod crash, deployment timeout, health check failed, readiness probe, liveness probe, restart loop",
            "Kubernetes deployment failed because the pod did not pass health checks",
            "Review pod logs and adjust readiness probe timeouts and thresholds" },
        { "Kubernetes", "HIGH",
            "namespace error, pod evicted, resource quota, node pressure, OOMKilled, CrashLoopBackOff, ImagePullBackOff",
            "Kubernetes resource exhaustion caused pod eviction or container restart loop",
            "Audit resource requests and limits and scale the cluster if needed" },
        { "Database", "CRITICAL",
            "connection pool, query timeout, deadlock detected, schema migration, constraint violation, table not found, connection refused",
            "Database connection pool exhausted or a long-running query caused a deadlock",
            "Tune connection pool size, add query timeouts, and review slow query logs" },
        { "CI", "MEDIUM",
            "pipeline failed, test failure, coverage threshold, artifact upload, checkout failed, webhook error, runner offline",
            "CI pipeline failed due to test failures or infrastructure issues with the runner",
            "Review failing test output and ensure CI runners have adequate resources" },
        { "Pipeline", "MEDIUM",
            "stage failed, trigger condition, parameter invalid, workspace missing, credentials expired, timeout exceeded, retry limit",
            "Pipeline orchestration error caused by expired credentials or invalid stage configuration",
            "Rotate expired credentials and validate pipeline parameter definitions" }
    };

    /**
     * Generates {@code count} ErrorKnowledgeBase entries with varied categories,
     * realistic comma-separated error patterns, root causes, solutions, and severities.
     * Entries are distributed evenly across 10 categories.
     *
     * @param count total number of entries to generate (must be positive)
     * @return list of unsaved ErrorKnowledgeBase entities ready for batch insert
     */
    public static List<ErrorKnowledgeBase> generateEntries(int count) {
        List<ErrorKnowledgeBase> entries = new ArrayList<>(count);
        int numCategories = CATEGORY_TEMPLATES.length;

        for (int i = 0; i < count; i++) {
            String[] template = CATEGORY_TEMPLATES[i % numCategories];
            int variant = i / numCategories;

            String category = template[0];
            String severity = template[1];
            String basePattern = template[2];
            String rootCause = template[3] + " (variant " + variant + ")";
            String solution = template[4] + " (variant " + variant + ")";

            // Vary the error pattern slightly by appending a unique keyword token
            // so each entry has a distinct, matchable pattern.
            String errorPattern = basePattern + ", error-code-" + i;

            entries.add(ErrorKnowledgeBase.builder()
                    .errorPattern(errorPattern)
                    .category(category)
                    .rootCause(rootCause)
                    .solution(solution)
                    .severity(severity)
                    .build());
        }

        return entries;
    }
}
