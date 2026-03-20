package io.llmcompactor.core.parser;

import io.llmcompactor.core.BuildError;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TestResult {
  private final int testsRun;
  private final int failures;
  private final List<BuildError> errors;
  private final List<Double> allDurations;

  public TestResult(
      int testsRun, int failures, List<BuildError> errors, List<Double> allDurations) {
    this.testsRun = testsRun;
    this.failures = failures;
    this.errors =
        errors != null ? Collections.unmodifiableList(errors) : Collections.<BuildError>emptyList();
    this.allDurations =
        allDurations != null
            ? Collections.unmodifiableList(allDurations)
            : Collections.<Double>emptyList();
  }

  public TestResult(int testsRun, int failures, List<BuildError> errors) {
    this(testsRun, failures, errors, Collections.emptyList());
  }

  public int testsRun() {
    return testsRun;
  }

  public int failures() {
    return failures;
  }

  public List<BuildError> errors() {
    return errors;
  }

  public List<Double> allDurations() {
    return allDurations;
  }

  // Jackson getters
  public int getTestsRun() {
    return testsRun;
  }

  public int getFailures() {
    return failures;
  }

  public List<BuildError> getErrors() {
    return errors;
  }

  public List<Double> getAllDurations() {
    return allDurations;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TestResult that = (TestResult) o;
    return testsRun == that.testsRun
        && failures == that.failures
        && Objects.equals(errors, that.errors)
        && Objects.equals(allDurations, that.allDurations);
  }

  @Override
  public int hashCode() {
    return Objects.hash(testsRun, failures, errors, allDurations);
  }

  @Override
  public String toString() {
    return "TestResult{testsRun="
        + testsRun
        + ", failures="
        + failures
        + ", errors="
        + errors.size()
        + "}";
  }
}
