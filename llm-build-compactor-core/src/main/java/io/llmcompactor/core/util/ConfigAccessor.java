package io.llmcompactor.core.util;

/** Utility for parsing configuration property values with consistent semantics. */
public final class ConfigAccessor {

  /** Case-insensitive "true" check. Returns false for null or non-"true" values. */
  public static boolean parseBoolean(String value) {
    return "true".equalsIgnoreCase(value);
  }

  /**
   * Case-insensitive "true" check with default fallback when value is null.
   *
   * @param value the string value to parse (may be null)
   * @param defaultValue returned when value is null
   */
  public static boolean parseBoolean(String value, boolean defaultValue) {
    return value != null ? parseBoolean(value) : defaultValue;
  }

  /**
   * Parses a double value with NumberFormatException fallback.
   *
   * @param value the string value to parse (may be null)
   * @param defaultValue returned when value is null or unparseable
   */
  public static double parseDouble(String value, double defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private ConfigAccessor() {}
}
