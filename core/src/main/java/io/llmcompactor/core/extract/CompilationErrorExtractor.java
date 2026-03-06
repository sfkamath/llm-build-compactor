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

  public static List<BuildError> extract(List<String> logs) {

    List<BuildError> errors = new ArrayList<>();

    for (String line : logs) {

      Matcher m = pattern.matcher(line);
      if (m.find()) {
        errors.add(
            new BuildError(
                "COMPILATION_ERROR", m.group(1), Integer.parseInt(m.group(2)), m.group(3), ""));
        continue;
      }

      Matcher mm = mavenPattern.matcher(line);
      if (mm.find()) {
        errors.add(
            new BuildError(
                "COMPILATION_ERROR", mm.group(1), Integer.parseInt(mm.group(2)), mm.group(4), ""));
      }
    }

    return errors;
  }

  private CompilationErrorExtractor() {}
}
