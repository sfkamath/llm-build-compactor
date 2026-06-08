package io.llmcompactor.core;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class BuildError {
  @JsonIgnore private final String type;
  private final String file;

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private final List<Integer> lines;

  private final String message;
  private final String stackTrace;

  @JsonInclude(JsonInclude.Include.NON_DEFAULT)
  private final double testDuration;

  @JsonIgnore private final String testLogs;

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
    this(type, file, toLinesList(line), message, stackTrace, 0.0, null);
  }

  public BuildError(
      String type,
      String file,
      int line,
      String message,
      String stackTrace,
      double testDuration,
      String testLogs) {
    this(type, file, toLinesList(line), message, stackTrace, testDuration, testLogs);
  }

  private static List<Integer> toLinesList(int line) {
    return line >= 0 ? Collections.singletonList(line) : Collections.emptyList();
  }

  /**
   * Returns test logs as a cleaned array with infrastructure noise filtered. Used for JSON
   * serialization.
   */
  @JsonProperty("testLogs")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<String> getTestLogsAsArray() {
    return SummaryWriter.processTestLogs(testLogs);
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
