package io.llmcompactor.core.parser;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.SlowTest;
import java.util.Collections;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
@ToString
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class TestResult {
  private final int testsRun;
  private final int failures;
  private final List<BuildError> errors;
  private final List<Double> allDurations;
  private final List<SlowTest> slowTests;

  public TestResult(
      int testsRun,
      int failures,
      List<BuildError> errors,
      List<Double> allDurations,
      List<SlowTest> slowTests) {
    this.testsRun = testsRun;
    this.failures = failures;
    this.errors =
        errors != null ? Collections.unmodifiableList(errors) : Collections.<BuildError>emptyList();
    this.allDurations =
        allDurations != null
            ? Collections.unmodifiableList(allDurations)
            : Collections.<Double>emptyList();
    this.slowTests =
        slowTests != null
            ? Collections.unmodifiableList(slowTests)
            : Collections.<SlowTest>emptyList();
  }

  public TestResult(
      int testsRun, int failures, List<BuildError> errors, List<Double> allDurations) {
    this(testsRun, failures, errors, allDurations, Collections.emptyList());
  }

  public TestResult(int testsRun, int failures, List<BuildError> errors) {
    this(testsRun, failures, errors, Collections.emptyList());
  }
}
