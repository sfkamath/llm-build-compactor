package io.llmcompactor.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class StackTraceCompressor {

  private static final List<String> FRAMEWORK_PREFIXES =
      Arrays.asList(
          "java.",
          "javax.",
          "sun.",
          "com.sun.",
          "jdk.",
          "org.junit.",
          "org.testng.",
          "org.apache.maven.",
          "org.gradle.",
          "org.springframework.",
          "org.hibernate.",
          "io.projectreactor.",
          "reactor.core.",
          "io.micronaut.",
          "io.netty.");

  // Note: io.micronaut. and io.netty. are included to filter out framework frames for most
  // projects.
  // For Micronaut/Netty projects, use stackFrameWhitelist to explicitly include your packages.

  /**
   * Normalizes a stack trace line by removing classloader prefixes like "app//", "bytebuddy.", etc.
   */
  private static String normalizeLine(String line) {
    if (line == null || line.isEmpty()) {
      return line;
    }
    // Remove classloader prefixes that Gradle adds
    return line.replaceAll("^\\s*at\\s+(?:app|bytebuddy|transformation|[0-9a-f]+)//", "at ");
  }

  /**
   * Compresses a stack trace into a single string containing only "useful" parts: - All project
   * frames (any frame NOT in the framework list) - Frames explicitly requested via stackFrameWhitelist
   * - "Caused by" lines that lead to useful frames
   */
  public static String compress(
      String stackTrace, String projectPackage, List<String> stackFrameWhitelist) {
    return compress(stackTrace, projectPackage, stackFrameWhitelist, Collections.emptyList());
  }

  /**
   * Compresses a stack trace with explicit whitelist and blacklist control.
   *
   * @param stackTrace the raw stack trace
   * @param projectPackage the project's base package
   * @param whitelist packages to always include (stackFrameWhitelist)
   * @param blacklist packages to always exclude (stackFrameBlacklist)
   */
  public static String compress(
      String stackTrace,
      String projectPackage,
      List<String> whitelist,
      List<String> blacklist) {
    if (stackTrace == null || stackTrace.isEmpty()) {
      return "";
    }

    String[] lines = stackTrace.split("\n");
    StringBuilder result = new StringBuilder();
    boolean hasContent = false;

    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.startsWith("at ")) {
        if (isUsefulFrame(trimmed, projectPackage, whitelist, blacklist)) {
          if (hasContent) {
            result.append("\n");
          }
          result.append(trimmed);
          hasContent = true;
        }
      } else if (trimmed.startsWith("Caused by:")) {
        if (hasContent) {
          result.append("\n");
        }
        result.append(trimmed);
        hasContent = true;
      }
    }

    return result.toString();
  }

  private static boolean isUsefulFrame(
      String trimmedLine, String projectPackage, List<String> stackFrameWhitelist) {
    return isUsefulFrame(trimmedLine, projectPackage, stackFrameWhitelist, Collections.emptyList());
  }

  private static boolean isUsefulFrame(
      String trimmedLine,
      String projectPackage,
      List<String> whitelist,
      List<String> blacklist) {
    // Normalize the line to remove classloader prefixes
    String normalized = normalizeLine(trimmedLine);

    // 0. Check whitelist FIRST - explicit inclusions override blacklist (when same package in both)
    if (whitelist != null) {
      for (String pkg : whitelist) {
        if (normalized.contains("at " + pkg)) {
          return true;
        }
      }
    }

    // 1. Check blacklist - explicit exclusions override defaults (but not whitelist)
    if (blacklist != null) {
      for (String pkg : blacklist) {
        if (normalized.contains("at " + pkg)) {
          return false;
        }
      }
    }

    // 2. If we have a project package, check for it (before framework exclusions)
    if (projectPackage != null && !projectPackage.isEmpty()) {
      if (normalized.contains(projectPackage)) {
        return true;
      }
    }

    // 3. Exclude known framework prefixes
    for (String prefix : FRAMEWORK_PREFIXES) {
      if (normalized.contains("at " + prefix)) {
        return false;
      }
    }

    // 4. Default: include it if it's not a known framework
    return true;
  }

  /**
   * Strips package prefixes from stack trace lines for more compact output. Example: "at
   * com.example.MyClass.myMethod(MyClass.java:10)" -> "at MyClass.myMethod(MyClass.java:10)"
   */
  public static String stripPackagePrefixes(String stackTrace) {
    if (stackTrace == null || stackTrace.isEmpty()) {
      return stackTrace;
    }

    return stackTrace.replaceAll(
        "(\\s*at\\s+)(?:[a-z0-9_]+\\.)*([A-Z][a-zA-Z0-9_$]*\\.[a-zA-Z0-9_$]*\\([^)]*\\))", "$1$2");
  }

  private StackTraceCompressor() {}
}
