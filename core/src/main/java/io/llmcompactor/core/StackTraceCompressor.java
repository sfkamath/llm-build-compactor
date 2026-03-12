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
  // Note: io.micronaut. is included to filter out framework frames for most projects.
  // For Micronaut projects, use includePackages to explicitly include your packages.

  /**
   * Normalizes a stack trace line by removing classloader prefixes like "app//", "bytebuddy.", etc.
   */
  private static String normalizeLine(String line) {
    if (line == null || line.isEmpty()) {
      return line;
    }
    // Remove classloader prefixes that Gradle adds
    String normalized = line.replaceAll("^\\s*at\\s+(?:app|bytebuddy|transformation|[0-9a-f]+)//", "at ");
    return normalized;
  }

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
    // Normalize the line to remove classloader prefixes
    String normalized = normalizeLine(trimmedLine);
    
    // 1. If we have a project package, check for it FIRST (before framework exclusions)
    if (projectPackage != null && !projectPackage.isEmpty()) {
      if (normalized.contains(projectPackage)) {
        return true;
      }
    }
    
    // 2. Exclude known framework prefixes (this takes precedence over includePackages)
    for (String prefix : FRAMEWORK_PREFIXES) {
      if (normalized.contains("at " + prefix)) {
        return false;
      }
    }
    
    // 3. Check explicit inclusions (only for non-framework packages)
    if (includePackages != null) {
      for (String pkg : includePackages) {
        if (normalized.contains("at " + pkg)) {
          return true;
        }
      }
    }

    // 4. Default: include it if it's not a known framework
    return true;
  }

  private StackTraceCompressor() {}
}
