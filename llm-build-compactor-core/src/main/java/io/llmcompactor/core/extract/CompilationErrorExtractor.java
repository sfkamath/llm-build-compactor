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

  private static final Pattern fatalErrorPattern = Pattern.compile("Fatal error compiling: (.+)");

  private static final Pattern ANSI_PATTERN = Pattern.compile("\\x1B\\[[0-9;]*m");

  private static String stripAnsi(String line) {
    return line == null ? null : ANSI_PATTERN.matcher(line).replaceAll("");
  }

  public static List<BuildError> extract(List<String> logs) {

    List<BuildError> errors = new ArrayList<>();
    int i = 0;

    while (i < logs.size()) {
      String line = stripAnsi(logs.get(i));

      Matcher m = pattern.matcher(line);
      if (m.find()) {
        String message = m.group(3);
        StringBuilder extraDetails = new StringBuilder();

        int j = i + 1;
        while (j < logs.size()) {
          String nextLine = stripAnsi(logs.get(j));
          if (nextLine.startsWith("  symbol:") || nextLine.startsWith("  location:")) {
            extraDetails.append("\n").append(nextLine);
            j++;
          } else {
            break;
          }
        }

        if (extraDetails.length() > 0) {
          message = message + extraDetails;
        }

        errors.add(new BuildError("COMPILATION_ERROR", m.group(1), Integer.parseInt(m.group(2)), message, ""));
        i = j;
        continue;
      }

      Matcher mm = mavenPattern.matcher(line);
      if (mm.find()) {
        String message = mm.group(4);
        StringBuilder extraDetails = new StringBuilder();

        int j = i + 1;
        while (j < logs.size()) {
          String nextLine = stripAnsi(logs.get(j));
          if (nextLine.startsWith("  symbol:") || nextLine.startsWith("  location:")) {
            extraDetails.append("\n").append(nextLine);
            j++;
          } else {
            break;
          }
        }

        if (extraDetails.length() > 0) {
          message = message + extraDetails;
        }

        errors.add(new BuildError("COMPILATION_ERROR", mm.group(1), Integer.parseInt(mm.group(2)), message, ""));
        i = j;
        continue;
      }

      Matcher fm = fatalErrorPattern.matcher(line);
      if (fm.find()) {
        errors.add(new BuildError("COMPILATION_ERROR", "pom.xml", 1, fm.group(1), line));
      }
      i++;
    }

    return errors;
  }

  private CompilationErrorExtractor() {}
}
