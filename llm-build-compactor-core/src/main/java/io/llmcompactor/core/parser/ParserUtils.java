package io.llmcompactor.core.parser;

final class ParserUtils {

  /** Returns the first non-empty line of a message, or an empty string. */
  static String extractFirstLine(String message) {
    if (message == null || message.isEmpty()) {
      return "";
    }
    return message.split("\n")[0].trim();
  }

  private ParserUtils() {}
}
