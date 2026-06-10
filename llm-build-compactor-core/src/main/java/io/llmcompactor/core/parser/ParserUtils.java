package io.llmcompactor.core.parser;

import io.llmcompactor.core.PackageDiscoverer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ParserUtils {

  /** Returns the first non-empty line of a message, or an empty string. */
  public static String extractFirstLine(String message) {
    if (message == null || message.isEmpty()) {
      return "";
    }
    return message.split("\n")[0].trim();
  }

  /**
   * Resolves the source file path from a JVM stack frame line such as {@code at
   * com.example.MyClass.method(MyFile.java:42)}. Returns null if the frame is not in the expected
   * format.
   *
   * @return resolved source path, or null if the frame cannot be parsed
   */
  public static String resolveFrameSource(String frameLine) {
    int lastColon = frameLine.lastIndexOf(':');
    int lastParen = frameLine.lastIndexOf(')');
    if (lastColon < 0 || lastParen <= lastColon) return null;
    int openParen = frameLine.lastIndexOf('(', lastColon);
    if (openParen < 0) return null;

    String fileName = frameLine.substring(openParen + 1, lastColon);
    if (!fileName.endsWith(".java") && !fileName.endsWith(".groovy")) return null;

    int atIndex = frameLine.indexOf("at ");
    if (atIndex < 0 || openParen <= atIndex + 3) return null;

    String classAndMethod = frameLine.substring(atIndex + 3, openParen);
    int methodDot = classAndMethod.lastIndexOf('.');
    if (methodDot < 0) return fileName;

    String fullClassName = classAndMethod.substring(0, methodDot);
    int classDot = fullClassName.lastIndexOf('.');
    if (classDot < 0) return fileName;

    String packageName = fullClassName.substring(0, classDot);
    return resolveSourceFile(packageName, fileName);
  }

  /**
   * Resolves a source file path from its package name and file name, searching standard Maven/
   * Gradle source roots. Falls back to {@code src/test/java/...} if not found on disk.
   *
   * @return resolved source path, or the filename unchanged if the package is empty
   */
  public static String resolveSourceFile(String packageName, String fileName) {
    if (packageName == null || packageName.isEmpty()) {
      return fileName;
    }
    List<Path> roots =
        Arrays.asList(
            Paths.get("src/main/java/"),
            Paths.get("src/test/java/"),
            Paths.get("src/it/java/"),
            Paths.get("src/integration-test/java/"));
    String resolved = PackageDiscoverer.resolveSourceFile(packageName, fileName, roots);
    if (resolved != null) return resolved;
    return "src/test/java/" + packageName.replace('.', '/') + '/' + fileName;
  }

  public static List<String> splitCsv(String value) {
    if (value == null || value.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<>();
    for (String part : value.split(",")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        result.add(trimmed);
      }
    }
    return result;
  }
}
