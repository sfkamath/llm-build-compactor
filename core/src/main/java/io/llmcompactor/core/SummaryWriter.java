package io.llmcompactor.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public class SummaryWriter {
  private static final ObjectMapper mapper =
      new ObjectMapper()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

  public static void write(BuildSummary summary, Path path) {
    try {
      Path parent = path.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(path, toJson(summary));
    } catch (IOException e) {
      throw new RuntimeException("Failed to write build summary", e);
    }
  }

  public static String toJson(BuildSummary summary) {
    if (summary == null) {
      return "{}";
    }
    try {
      // Create a simplified version for JSON if we want to condense snippets or stack traces
      BuildSummary condensed =
          new BuildSummary(
              summary.status(),
              summary.testsRun(),
              summary.failures(),
              summary.errors().stream()
                  .map(
                      e ->
                          new BuildError(
                              e.type(),
                              e.file(),
                              e.line(),
                              condenseForJson(e.message()),
                              condenseForJson(e.stackTrace()),
                              e.testDuration()))
                  .collect(Collectors.toList()),
              summary.fixTargets().stream()
                  .map(
                      t ->
                          new FixTarget(
                              t.file(), t.line(), condenseForJson(t.reason()), t.snippet()))
                  .collect(Collectors.toList()),
              summary.recentChanges(),
              summary.totalBuildDurationMs(),
              summary.testDurationPercentiles());
      return mapper.writeValueAsString(condensed);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize build summary to JSON", e);
    }
  }

  public static String toHumanReadable(BuildSummary summary, boolean showTestDuration) {
    StringBuilder sb = new StringBuilder();
    sb.append("=== LLM Build Summary ===\n");
    sb.append("Status: ").append(summary.status()).append("\n");
    sb.append("Tests Run: ").append(summary.testsRun()).append("\n");
    sb.append("Failures: ").append(summary.failures()).append("\n");

    if (summary.totalBuildDurationMs() != null) {
      sb.append("Total Build Duration: ").append(summary.totalBuildDurationMs()).append("ms\n");
    }

    if (summary.testDurationPercentiles() != null && !summary.testDurationPercentiles().isEmpty()) {
      sb.append("Test Duration Percentiles (ms):\n");
      summary.testDurationPercentiles().entrySet().stream()
          .forEach(
              e ->
                  sb.append("  ")
                      .append(e.getKey())
                      .append(": ")
                      .append(String.format("%.2f", e.getValue()))
                      .append("\n"));
    }

    if (!summary.recentChanges().isEmpty()) {
      sb.append("\nRecent Changes:\n");
      summary.recentChanges().forEach(file -> sb.append("  - ").append(file).append("\n"));
    }

    if (!summary.errors().isEmpty()) {
      sb.append("\nErrors:\n");
      for (BuildError error : summary.errors()) {
        sb.append("  - ").append(error.type());
        if (error.file() != null) {
          sb.append(" at ").append(error.file());
          if (error.line() > 0) {
            sb.append(":").append(error.line());
          }
        }
        if (showTestDuration && error.testDuration() > 0) {
          sb.append(" (").append(String.format("%.2f", error.testDuration())).append("ms)");
        }
        sb.append("\n");
        if (error.message() != null) {
          sb.append("    ").append(error.message().replace("\n", "\n    ")).append("\n");
        }
        if (error.stackTrace() != null) {
          sb.append("    Stack trace:\n");
          String indent = "        ";
          sb.append(indent).append(error.stackTrace().replace("\n", "\n" + indent)).append("\n");
        }
      }
    }

    if (!summary.fixTargets().isEmpty()) {
      sb.append("\nFix Targets:\n");
      for (FixTarget target : summary.fixTargets()) {
        sb.append("  - ").append(target.file()).append(":").append(target.line()).append("\n");
        sb.append("    Reason: ").append(target.reason()).append("\n");
        if (target.snippet() != null) {
          sb.append("    Snippet:\n");
          String indent = "      ";
          sb.append(indent).append(target.snippet().replace("\n", "\n" + indent)).append("\n");
        }
      }
    }

    return sb.toString();
  }

  private static String condenseForJson(String text) {
    if (text == null) {
      return null;
    }
    // Condense internal whitespace (2+ spaces/tabs) to a single space for JSON only
    return text.replaceAll("[ \\t]{2,}", " ");
  }

  private SummaryWriter() {
  }
}
