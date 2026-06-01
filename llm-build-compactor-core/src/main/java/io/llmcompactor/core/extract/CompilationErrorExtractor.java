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
  private static final Pattern UNICODE_ESCAPE_PATTERN = Pattern.compile("\\\\u001[B]\\[[0-9;]*m");
  private static final Pattern HTML_ENCODED_ANSI_PATTERN =
      Pattern.compile("(?:&amp)?#27;\\[[0-9;]*m");

  public static String stripAnsi(String line) {
    if (line == null) return null;
    line = ANSI_PATTERN.matcher(line).replaceAll("");
    line = UNICODE_ESCAPE_PATTERN.matcher(line).replaceAll("");
    line = HTML_ENCODED_ANSI_PATTERN.matcher(line).replaceAll("");
    return line;
  }

  public static List<BuildError> extract(List<String> logs) {

    List<BuildError> errors = new ArrayList<>();
    int i = 0;

    while (i < logs.size()) {
      String line = stripAnsi(logs.get(i));

      Matcher m = pattern.matcher(line);
      if (m.find()) {
        int j = collectContinuationLines(logs, i + 1);
        String message = appendContinuation(m.group(3), logs, i + 1, j);
        errors.add(
            new BuildError(
                "COMPILATION_ERROR", m.group(1), Integer.parseInt(m.group(2)), message, ""));
        i = j;
        continue;
      }

      Matcher mm = mavenPattern.matcher(line);
      if (mm.find()) {
        int j = collectContinuationLines(logs, i + 1);
        String message = appendContinuation(mm.group(4), logs, i + 1, j);
        errors.add(
            new BuildError(
                "COMPILATION_ERROR", mm.group(1), Integer.parseInt(mm.group(2)), message, ""));
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

  /** Returns the index of the first line after i that is NOT a symbol/location continuation. */
  private static int collectContinuationLines(List<String> logs, int start) {
    int j = start;
    while (j < logs.size()) {
      String next = stripAnsi(logs.get(j));
      if (next.startsWith("  symbol:") || next.startsWith("  location:")) {
        j++;
      } else {
        break;
      }
    }
    return j;
  }

  private static String appendContinuation(String base, List<String> logs, int from, int to) {
    if (from >= to) {
      return base;
    }
    StringBuilder sb = new StringBuilder(base);
    for (int k = from; k < to; k++) {
      sb.append("\n").append(stripAnsi(logs.get(k)));
    }
    return sb.toString();
  }

  private CompilationErrorExtractor() {}
}
