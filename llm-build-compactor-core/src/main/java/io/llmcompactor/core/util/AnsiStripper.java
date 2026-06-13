package io.llmcompactor.core.util;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/** Utility for stripping ANSI escape codes and terminal formatting from text. */
@UtilityClass
public class AnsiStripper {
  /** ANSI escape code pattern for terminal colors (\x1B[m) */
  private static final Pattern ANSI_PATTERN = Pattern.compile("\\x1B\\[[0-9;]*m");

  /** Unicode escape form of ANSI codes (\\u001B[m) */
  private static final Pattern UNICODE_ESCAPE_PATTERN = Pattern.compile("\\\\u001[B]\\[[0-9;]*m");

  /** HTML-encoded ANSI escape codes (&amp#27; or &amp#27; forms) */
  private static final Pattern HTML_ENCODED_ANSI_PATTERN =
      Pattern.compile("(?:&amp)?#27;\\[[0-9;]*m");

  /** Strips all ANSI escape codes, unicode escapes, and HTML-encoded ANSI codes from the text. */
  public static String stripAnsi(String text) {
    if (text == null) {
      return null;
    }
    String result = ANSI_PATTERN.matcher(text).replaceAll("");
    result = UNICODE_ESCAPE_PATTERN.matcher(result).replaceAll("");
    return HTML_ENCODED_ANSI_PATTERN.matcher(result).replaceAll("");
  }
}
