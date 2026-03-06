package io.llmcompactor.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SummaryWriter {
    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static void write(BuildSummary summary, Path path) {
        try {
            Files.createDirectories(path.getParent());
            mapper.writeValue(path.toFile(), summary);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write build summary to " + path, e);
        }
    }

    public static String toJson(BuildSummary summary) {
        try {
            return mapper.writeValueAsString(summary);
        } catch (IOException e) {
            return "{\"status\": \"ERROR\", \"message\": \"Failed to serialize summary\"}";
        }
    }

    public static String toHumanReadable(BuildSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== LLM Build Summary ===\n");
        sb.append("Status: ").append(summary.status()).append("\n");
        sb.append("Tests Run: ").append(summary.testsRun()).append("\n");
        sb.append("Failures: ").append(summary.failures()).append("\n");
        sb.append("\n");

        if (summary.errors() != null && !summary.errors().isEmpty()) {
            sb.append("Errors:\n");
            for (BuildError error : summary.errors()) {
                sb.append("  - ").append(error.type()).append(" at ")
                  .append(error.file()).append(":").append(error.line()).append("\n");
                sb.append("    ").append(error.message()).append("\n");
                if (error.stackTrace() != null && !error.stackTrace().isEmpty()) {
                    sb.append("    Stack trace:\n");
                    String msg = error.message().trim();
                    for (String frame : error.stackTrace().split("\n")) {
                        String trimmedFrame = frame.trim();
                        if (trimmedFrame.isEmpty() || trimmedFrame.equals(msg)) {
                            continue;
                        }
                        sb.append("      ").append(frame).append("\n");
                    }
                }
            }
            sb.append("\n");
        }

        if (summary.fixTargets() != null && !summary.fixTargets().isEmpty()) {
            sb.append("Fix Targets:\n");
            for (FixTarget target : summary.fixTargets()) {
                sb.append("  - ").append(target.file()).append(":").append(target.line()).append("\n");
                sb.append("    Reason: ").append(target.reason().split("\n")[0]).append("\n");
            }
            sb.append("\n");
        }

        if (summary.recentChanges() != null && !summary.recentChanges().isEmpty()) {
            sb.append("Recent Changes:\n");
            for (String change : summary.recentChanges()) {
                sb.append("  - ").append(change).append("\n");
            }
        }

        return sb.toString();
    }
}
