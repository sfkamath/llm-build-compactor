package io.llmcompactor.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.llmcompactor.core.util.AnsiStripper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SummaryWriter {
  private static final ObjectMapper mapper =
      new ObjectMapper()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

  private static final double DEFAULT_TEST_DURATION_THRESHOLD_MS =
      CompactorConfig.DEFAULT_TEST_DURATION_THRESHOLD_MS;

  /**
   * Normalizes a BuildSummary for output by applying all message/stack trace cleaning once. This
   * ensures consistent output and avoids duplicating normalization logic.
   */
  private static BuildSummary normalize(BuildSummary summary, double testDurationThresholdMs) {
    if (summary == null) {
      return null;
    }
    return new BuildSummary(
        summary.status(),
        summary.testsRun(),
        summary.failures(),
        summary.errors().stream()
            .map(
                e ->
                    new BuildError(
                        e.type(),
                        e.file(),
                        e.lines(),
                        stripExceptionPackage(e.message()),
                        StackTraceCompressor.stripPackagePrefixes(e.stackTrace()),
                        e.testDuration() >= testDurationThresholdMs ? e.testDuration() : 0.0,
                        e.testLogs()))
            .collect(Collectors.toList()),
        summary.fixTargets(),
        summary.recentChanges(),
        summary.totalBuildDurationMs(),
        summary.testDurationPercentiles(),
        summary.slowTests());
  }

  /** SLF4J infrastructure noise patterns to filter */
  private static final Pattern SLF4J_NOISE_PATTERN = Pattern.compile("^SLF4J:.*$");

  /** Test/runtime bootstrap loggers that obscure per-test diagnostics. */
  private static final Pattern BOOTSTRAP_LOGGER_PATTERN =
      Pattern.compile(
          "^(o\\.testcontainers\\.|o\\.t\\.|tc\\.|i\\.m\\.c\\.DefaultApplicationContext\\$RuntimeConfiguredEnvironment\\b).*");

  /** Log timestamp pattern: HH:mm:ss.SSS */
  private static final Pattern TIMESTAMP_PATTERN =
      Pattern.compile("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s*");

  /** Date pattern: MMM DD, YYYY HH:MM:SS AM/PM (java.util.logging format used by Liquibase etc.) */
  private static final Pattern DATE_PATTERN =
      Pattern.compile(
          "^[A-Z][a-z]{2}\\s+\\d{1,2},\\s+\\d{4}\\s+\\d{1,2}:\\d{2}:\\d{2}\\s+[AP]M\\s*");

  /** Log thread pattern: [thread-name] - only at start after timestamp */
  private static final Pattern THREAD_PATTERN = Pattern.compile("^\\s*\\[[^\\]]+\\]\\s*");

  /** Log level pattern: INFO/DEBUG/WARN/ERROR (space or colon+space separator) */
  private static final Pattern LEVEL_PATTERN =
      Pattern.compile("(INFO|DEBUG|WARN|ERROR|TRACE)(\\s+|:\\s+)");

  /** Logger name pattern: abbreviated or full package.class */
  private static final Pattern LOGGER_PATTERN = Pattern.compile("[a-z][a-zA-Z0-9_.]*\\s*-\\s*");

  /** Cleans up test log lines by removing infrastructure noise and normalizing format. */
  public static String cleanTestLogLine(String line) {
    if (line == null || line.isEmpty()) {
      return line;
    }

    // Preserve GradleParser section headers ([system-out], [system-err]) unchanged
    if (line.startsWith("[system-out]") || line.startsWith("[system-err]")) {
      return line;
    }

    // Filter SLF4J infrastructure noise
    if (SLF4J_NOISE_PATTERN.matcher(line).matches()) {
      return null;
    }

    String result = line;

    // Strip timestamp (HH:mm:ss.SSS)
    result = TIMESTAMP_PATTERN.matcher(result).replaceFirst("");
    // Strip date prefix (MMM DD, YYYY HH:MM:SS AM/PM — java.util.logging format used by Liquibase)
    result = DATE_PATTERN.matcher(result).replaceFirst("");

    // Strip thread info
    result = THREAD_PATTERN.matcher(result).replaceFirst(" ");

    // Strip log level
    result = LEVEL_PATTERN.matcher(result).replaceAll("");

    // Strip logger name (but keep the message after the dash)
    // Disabled: users need to see the class name in test logs to debug failures
    // result = LOGGER_PATTERN.matcher(result).replaceAll("");

    // Strip ANSI escape codes (terminal colors), including HTML-encoded forms
    result = AnsiStripper.stripAnsi(result);

    // Normalize whitespace and trim for consistent alignment
    String trimmed = result.replaceAll("\\s+", " ").trim();

    // Filter out boilerplate framework stacktrace frames (micronaut, netty, spring, etc.)
    // Check on the trimmed form since leading tabs hide the "at " prefix
    if (trimmed.startsWith("at ") && StackTraceCompressor.isFrameworkFrame(trimmed)) {
      return null;
    }

    // Filter out infrastructure noise (Liquibase migration logs, log level config, etc.)
    if (trimmed.contains("liquibase")
        || trimmed.contains("PropertiesLoggingLevelsConfigurer")
        || BOOTSTRAP_LOGGER_PATTERN.matcher(trimmed).matches()) {
      return null;
    }

    // Convert leading tab to 2 spaces for stacktrace frames to maintain visual hierarchy
    // This is consistent regardless of terminal tab width settings
    boolean hasLeadingTab = result.startsWith("\t");
    result = trimmed;
    if (hasLeadingTab && result.startsWith("at ")) {
      result = "  " + result;
    }

    return result.isEmpty() ? null : result;
  }

  /** Processes test logs, cleaning up noise and returning as array of lines. */
  public static List<String> processTestLogs(String testLogs) {
    if (testLogs == null || testLogs.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> result = new ArrayList<>();
    for (String line : testLogs.split("\n")) {
      String cleaned = cleanTestLogLine(line);
      if (cleaned != null) {
        result.add(cleaned);
      }
    }
    return result;
  }

  public static void write(BuildSummary summary, Path path) {
    try {
      Path parent = path.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.write(path, toJson(summary).getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new RuntimeException("Failed to write build summary", e);
    }
  }

  public static String toJson(BuildSummary summary) {
    return toJson(summary, DEFAULT_TEST_DURATION_THRESHOLD_MS);
  }

  public static String toJson(BuildSummary summary, double testDurationThresholdMs) {
    if (summary == null) {
      return "{}";
    }
    try {
      // Normalize once before serialization (cleans messages, stack traces, applies threshold)
      BuildSummary normalized = normalize(summary, testDurationThresholdMs);
      // Create a simplified version for JSON if we want to condense snippets or stack traces
      BuildSummary condensed =
          new BuildSummary(
              normalized.status(),
              normalized.testsRun(),
              normalized.failures(),
              normalized.errors().stream()
                  .map(
                      e -> {
                        // Mutate lines: if single item, clear array so @JsonInclude(NON_EMPTY)
                        // omits it
                        // This reduces JSON size: "lines":[41] becomes no lines field (file:line is
                        // in message context)
                        List<Integer> lines = e.lines();
                        if (lines != null && lines.size() == 1) {
                          lines = Collections.emptyList();
                        }
                        return new BuildError(
                            e.type(),
                            e.file(),
                            lines,
                            condenseForJson(e.message()),
                            condenseForJson(e.stackTrace()),
                            e.testDuration(),
                            e.testLogs() != null ? condenseForJson(e.testLogs()) : null);
                      })
                  .collect(Collectors.toList()),
              normalized.fixTargets().stream()
                  .map(
                      t ->
                          new FixTarget(
                              t.file(), t.line(), condenseForJson(t.reason()), t.snippet()))
                  .collect(Collectors.toList()),
              summary.recentChanges(),
              summary.totalBuildDurationMs(),
              summary.testDurationPercentiles(),
              normalized.slowTests());
      return mapper.writeValueAsString(condensed);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize build summary to JSON", e);
    }
  }

  /**
   * Strips package prefix from exception type in error messages. Example:
   * "org.opentest4j.AssertionFailedError: message" -> "AssertionFailedError: message"
   */
  public static String stripExceptionPackage(String message) {
    if (message == null || message.isEmpty()) {
      return message;
    }
    // Match pattern: package.ExceptionType: message
    return message.replaceAll("^([a-z][a-zA-Z0-9_]*\\.)+([A-Z][a-zA-Z0-9_$]*:.*)$", "$2");
  }

  public static String toHumanReadable(BuildSummary summary, boolean showTestDuration) {
    return toHumanReadable(summary, showTestDuration, DEFAULT_TEST_DURATION_THRESHOLD_MS);
  }

  public static String toHumanReadable(
      BuildSummary summary, boolean showTestDuration, double testDurationThresholdMs) {
    // Normalize once before rendering (cleans messages, stack traces)
    summary = normalize(summary, testDurationThresholdMs);

    StringBuilder sb = new StringBuilder();
    sb.append("=== LLM Build Compactor Summary ===\n");
    sb.append("Status: ").append(summary.status()).append("\n");
    sb.append("Tests Run: ").append(summary.testsRun()).append("\n");
    sb.append("Failures: ").append(summary.failures()).append("\n");

    if (summary.totalBuildDurationMs() != null) {
      sb.append("Total Build Duration: ").append(summary.totalBuildDurationMs()).append("ms\n");
    }

    if (summary.testDurationPercentiles() != null && !summary.testDurationPercentiles().isEmpty()) {
      sb.append("Test Duration Percentiles (ms):\n");
      summary
          .testDurationPercentiles()
          .forEach(
              (key, value) ->
                  sb.append("  ")
                      .append(key)
                      .append(": ")
                      .append(String.format("%.2f", value))
                      .append("\n"));
    }

    if (!summary.recentChanges().isEmpty()) {
      sb.append("\nRecent Changes:\n");
      summary.recentChanges().forEach(file -> sb.append("  - ").append(file).append("\n"));
    }

    if (!summary.errors().isEmpty()) {
      sb.append("\nErrors:\n");
      for (BuildError error : summary.errors()) {
        sb.append("  - ");
        if (error.file() != null) {
          sb.append(error.file());
          if (error.lines() != null && !error.lines().isEmpty()) {
            sb.append(":")
                .append(
                    error.lines().stream().map(String::valueOf).collect(Collectors.joining(", ")));
          }
        } else {
          sb.append(error.type());
        }
        if (showTestDuration && error.testDuration() >= testDurationThresholdMs) {
          sb.append(" (").append(String.format("%.2f", error.testDuration())).append("ms)");
        }
        sb.append("\n");
        if (error.message() != null) {
          sb.append("    ").append(error.message().replace("\n", "\n    ")).append("\n");
        }
        if (error.stackTrace() != null) {
          String indent = "        ";
          String compressed = StackTraceCompressor.stripPackagePrefixes(error.stackTrace());
          sb.append(indent).append(compressed.replace("\n", "\n" + indent)).append("\n");
        }
        if (error.testLogs() != null && !error.testLogs().isEmpty()) {
          List<String> cleanedLogs = processTestLogs(error.testLogs());
          if (!cleanedLogs.isEmpty()) {
            sb.append("    Test logs (").append(error.file()).append("):\n");
            String indent = "        ";
            for (String logLine : cleanedLogs) {
              sb.append(indent).append(logLine).append("\n");
            }
          }
        }
      }
    }

    if (!summary.slowTests().isEmpty()) {
      sb.append("\nSlow Tests:\n");
      for (SlowTest slow : summary.slowTests()) {
        sb.append("  - ").append(slow.className()).append("#").append(slow.testName());
        sb.append(" (").append(String.format("%.2f", slow.testDuration())).append("ms)\n");
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
}
