package com.opsera.pipelineassistant.golden;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Utilities for loading golden-file JSON and fixture log texts from the test classpath.
 * Golden files live in src/test/resources/golden/
 * Fixture files live in src/test/resources/fixtures/
 */
public final class GoldenFileTestHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GoldenFileTestHelper() {}

    /**
     * Loads the text content of a fixture file from the test classpath.
     *
     * @param classpathPath path relative to classpath root (e.g. "fixtures/oom-error-log.txt")
     * @return file content as UTF-8 String
     * @throws IOException if the resource cannot be found or read
     */
    public static String loadFixture(String classpathPath) throws IOException {
        ClassLoader cl = GoldenFileTestHelper.class.getClassLoader();
        try (InputStream is = cl.getResourceAsStream(classpathPath)) {
            if (is == null) {
                throw new IOException("Fixture file not found on classpath: " + classpathPath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Loads and deserializes a golden-file JSON from src/test/resources/golden/.
     *
     * @param filename golden file name only (e.g. "oom-error.json")
     * @return deserialized {@link GoldenResponse}
     * @throws IOException if the file cannot be found or deserialized
     */
    public static GoldenResponse loadGolden(String filename) throws IOException {
        String classpathPath = "golden/" + filename;
        ClassLoader cl = GoldenFileTestHelper.class.getClassLoader();
        try (InputStream is = cl.getResourceAsStream(classpathPath)) {
            if (is == null) {
                throw new IOException("Golden file not found on classpath: " + classpathPath);
            }
            return MAPPER.readValue(is, GoldenResponse.class);
        }
    }

    /**
     * Plain-field POJO matching the six behavioral fields captured in each golden JSON file.
     * Extra JSON properties (if any) are silently ignored to allow forward compatibility.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GoldenResponse {
        public String category;
        public String rootCause;
        public String suggestedFix;
        public String customerUpdate;
        public String severity;
        public int confidence;
    }
}
