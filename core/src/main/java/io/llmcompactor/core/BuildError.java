package io.llmcompactor.core;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class BuildError {
  private final String type;
  private final String file;
  private final List<Integer> lines;
  private final String message;
  private final String stackTrace;
  private final double testDuration;

  public BuildError(
      String type,
      String file,
      List<Integer> lines,
      String message,
      String stackTrace,
      double testDuration) {
    this.type = type;
    this.file = file;
    this.lines =
        lines != null ? Collections.unmodifiableList(lines) : Collections.<Integer>emptyList();
    this.message = message;
    this.stackTrace = stackTrace;
    this.testDuration = testDuration;
  }

  public BuildError(String type, String file, int line, String message, String stackTrace) {
    this(type, file, Collections.singletonList(line), message, stackTrace, 0.0);
  }

  public BuildError(
      String type, String file, int line, String message, String stackTrace, double testDuration) {
    this(type, file, Collections.singletonList(line), message, stackTrace, testDuration);
  }

  public String type() {
    return type;
  }

  public String file() {
    return file;
  }

  public List<Integer> lines() {
    return lines;
  }

  public String message() {
    return message;
  }

  public String stackTrace() {
    return stackTrace;
  }

  public double testDuration() {
    return testDuration;
  }

  // Jackson getters
  public String getType() {
    return type;
  }

  public String getFile() {
    return file;
  }

  public List<Integer> getLines() {
    return lines;
  }

  public String getMessage() {
    return message;
  }

  public String getStackTrace() {
    return stackTrace;
  }

  public double getTestDuration() {
    return testDuration;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BuildError that = (BuildError) o;
    return Double.compare(that.testDuration, testDuration) == 0
        && Objects.equals(type, that.type)
        && Objects.equals(file, that.file)
        && Objects.equals(lines, that.lines)
        && Objects.equals(message, that.message)
        && Objects.equals(stackTrace, that.stackTrace);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, file, lines, message, stackTrace, testDuration);
  }

  @Override
  public String toString() {
    return "BuildError{type='"
        + type
        + "', file='"
        + file
        + "', lines="
        + lines
        + ", message='"
        + message
        + "', testDuration="
        + testDuration
        + "}";
  }
}
