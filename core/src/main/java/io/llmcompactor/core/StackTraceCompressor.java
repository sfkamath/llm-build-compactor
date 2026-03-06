package io.llmcompactor.core;

import java.util.List;

public final class StackTraceCompressor {

  private static final List<String> FRAMEWORK_PREFIXES =
      List.of(
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
          "io.micronaut.");

  /**
   * Compresses a stack trace into a single string containing only "useful" parts: - All project
   * frames (any frame NOT in the framework list) - Frames explicitly requested via includePackages
   * - "Caused by" lines that lead to useful frames
   */
  public static String compress(
      String stackTrace, String projectPackage, List<String> includePackages) {
    if (stackTrace == null || stackTrace.isEmpty()) {
      return "";
    }

    String[] lines = stackTrace.split("\n");
    StringBuilder result = new StringBuilder();
    boolean hasContent = false;

    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.startsWith("at ")) {
        if (isUsefulFrame(trimmed, projectPackage, includePackages)) {
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
      String trimmedLine, String projectPackage, List<String> includePackages) {
    // 1. Check explicit inclusions (overrides defaults)
    if (includePackages != null) {
      for (String pkg : includePackages) {
        if (trimmedLine.contains("at " + pkg)) {
          return true;
        }
      }
    }

    // 2. If we have a project package, check for it
    if (projectPackage != null && !projectPackage.isEmpty()) {
      if (trimmedLine.contains(projectPackage)) {
        return true;
      }
    }

    // 3. Exclude known framework prefixes
    for (String prefix : FRAMEWORK_PREFIXES) {
      if (trimmedLine.contains("at " + prefix)) {
        return false;
      }
    }

    // 4. Default: include it if it's not a known framework
    return true;
  }

  private StackTraceCompressor() {}
}
