package io.llmcompactor.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class BuildError {
  @JsonIgnore private final String type;
  private final String file;
  private final List<Integer> lines;
  private final String message;
  private final String stackTrace;
  private final double testDuration;
  private final String testLogs;

  public BuildError(
      String type,
      String file,
      List<Integer> lines,
      String message,
      String stackTrace,
      double testDuration,
      String testLogs) {
    this.type = type;
    this.file = file;
    this.lines =
        lines != null ? Collections.unmodifiableList(lines) : Collections.<Integer>emptyList();
    this.message = message;
    this.stackTrace = stackTrace;
    this.testDuration = testDuration;
    this.testLogs = testLogs;
  }

  public BuildError(String type, String file, int line, String message, String stackTrace) {
    this(type, file, Collections.singletonList(line), message, stackTrace, 0.0, null);
  }

  public BuildError(
      String type,
      String file,
      int line,
      String message,
      String stackTrace,
      double testDuration,
      String testLogs) {
    this(type, file, Collections.singletonList(line), message, stackTrace, testDuration, testLogs);
  }

  @JsonIgnore
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

  public String testLogs() {
    return testLogs;
  }

  // Jackson getters
  @JsonIgnore
  public String getType() {
    return type;
  }

  public String getFile() {
    return file;
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<Integer> getLines() {
    return lines;
  }

  public String getMessage() {
    return message;
  }

  public String getStackTrace() {
    return stackTrace;
  }

  @JsonInclude(JsonInclude.Include.NON_DEFAULT)
  public double getTestDuration() {
    return testDuration;
  }

  @JsonIgnore
  public String getTestLogs() {
    return testLogs;
  }

  /**
   * Returns test logs as a cleaned array with infrastructure noise filtered. Used for JSON
   * serialization.
   */
  @JsonProperty("testLogs")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<String> getTestLogsAsArray() {
    if (testLogs == null || testLogs.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<>();
    for (String line : testLogs.split("\n")) {
      String cleaned = SummaryWriter.cleanTestLogLine(line);
      if (cleaned != null) {
        result.add(cleaned);
      }
    }
    return result;
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
        && Objects.equals(stackTrace, that.stackTrace)
        && Objects.equals(testLogs, that.testLogs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, file, lines, message, stackTrace, testDuration, testLogs);
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
        + ", testLogs="
        + (testLogs != null ? "present" : "none")
        + "}";
  }
}
