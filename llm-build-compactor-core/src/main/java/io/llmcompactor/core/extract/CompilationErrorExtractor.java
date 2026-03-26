package io.llmcompactor.core.extract;

import io.llmcompactor.core.BuildError;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CompilationErrorExtractor {

  private static final Pattern pattern =
      Pattern.compile("(?:\\[ERROR]\\s+)?(.+\\.java):(\\d+): (.+)");

  private static final Pattern mavenPattern =
      Pattern.compile("(?:\\[ERROR]\\s+)?(.+\\.java):\\[(\\d+),(\\d+)] (.+)");

  private static final Pattern fatalErrorPattern =
      Pattern.compile("Fatal error compiling: (.+)");

  private static final Pattern ANSI_PATTERN = Pattern.compile("\\x1B\\[[0-9;]*m");

  private static String stripAnsi(String line) {
    return line == null ? null : ANSI_PATTERN.matcher(line).replaceAll("");
  }

  public static List<BuildError> extract(List<String> logs) {

    List<BuildError> errors = new ArrayList<>();

    for (String line : logs) {
      String cleanedLine = stripAnsi(line);

      Matcher m = pattern.matcher(cleanedLine);
      if (m.find()) {
        errors.add(
            new BuildError(
                "COMPILATION_ERROR", m.group(1), Integer.parseInt(m.group(2)), m.group(3), ""));
        continue;
      }

      Matcher mm = mavenPattern.matcher(cleanedLine);
      if (mm.find()) {
        errors.add(
            new BuildError(
                "COMPILATION_ERROR", mm.group(1), Integer.parseInt(mm.group(2)), mm.group(4), ""));
        continue;
      }

      Matcher fm = fatalErrorPattern.matcher(cleanedLine);
      if (fm.find()) {
        errors.add(
            new BuildError("COMPILATION_ERROR", "pom.xml", 1, fm.group(1), cleanedLine));
      }
    }

    return errors;
  }

  private CompilationErrorExtractor() {}
}