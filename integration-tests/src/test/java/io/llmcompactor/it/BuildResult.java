package io.llmcompactor.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Result of a build execution containing output and parsed summary.
 */
public class BuildResult {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String output;
    private final String summaryJson;
    private final int exitCode;
    private final Path buildDir;
    private final Path projectDir;

    public BuildResult(String output, String summaryJson, int exitCode, Path buildDir, Path projectDir) {
        this.output = output;
        this.summaryJson = summaryJson;
        this.exitCode = exitCode;
        this.buildDir = buildDir;
        this.projectDir = projectDir;
    }

    public BuildResult(String output, String summaryJson, int exitCode, Path buildDir) {
        this(output, summaryJson, exitCode, buildDir, buildDir.getParent());
    }

    /**
     * Returns the full build output (stdout/stderr).
     */
    public String output() {
        return output;
    }

    /**
     * Returns the raw llm-summary.json content, or null if not found.
     */
    public String summaryJson() {
        return summaryJson;
    }

    /**
     * Returns the build exit code.
     */
    public int exitCode() {
        return exitCode;
    }

    /**
     * Returns the build directory (target/ or build/).
     */
    public Path buildDir() {
        return buildDir;
    }

    /**
     * Returns the project directory.
     */
    public Path projectDir() {
        return projectDir;
    }

    /**
     * Parses the summary JSON and returns the root JsonNode.
     * Returns null if no summary was generated.
     */
    public JsonNode summaryTree() throws IOException {
        if (summaryJson == null || summaryJson.trim().isEmpty()) {
            return null;
        }
        return MAPPER.readTree(summaryJson);
    }

    /**
     * Checks if the build output contains the expected string.
     */
    public boolean outputContains(String expected) {
        return output.contains(expected);
    }

    /**
     * Checks if the build output contains the LLM Build Compactor summary.
     */
    public boolean hasCompactorOutput() {
        return outputContains("LLM Build Compactor Summary") || summaryJson != null;
    }

    /**
     * Checks if the summary JSON contains a specific field.
     */
    public boolean hasField(String fieldName) throws IOException {
        JsonNode tree = summaryTree();
        return tree != null && tree.has(fieldName);
    }

    /**
     * Checks if the summary JSON contains a specific field with a non-empty value.
     */
    public boolean hasNonEmptyField(String fieldName) throws IOException {
        JsonNode tree = summaryTree();
        if (tree == null || !tree.has(fieldName)) {
            return false;
        }
        JsonNode value = tree.get(fieldName);
        if (value.isTextual()) {
            return !value.asText().isEmpty();
        }
        if (value.isArray() || value.isObject()) {
            return !value.isEmpty();
        }
        return true;
    }

    /**
     * Extracts JSON from build output by finding the first { and last }.
     */
    public static String extractJsonFromOutput(String output) {
        int start = output.indexOf('{');
        int end = output.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return output.substring(start, end + 1);
        }
        return null;
    }

    /**
     * Returns true if the build succeeded (exit code 0).
     */
    public boolean isSuccess() {
        return exitCode == 0;
    }

    /**
     * Returns true if the build failed (non-zero exit code).
     */
    public boolean isFailure() {
        return exitCode != 0;
    }
}
