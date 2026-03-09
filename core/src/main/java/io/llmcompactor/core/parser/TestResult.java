package io.llmcompactor.core.parser;

import io.llmcompactor.core.BuildError;
import java.util.List;

public record TestResult(
    int testsRun, int failures, List<BuildError> errors, List<Double> allDurations) {
  public TestResult(int testsRun, int failures, List<BuildError> errors) {
    this(testsRun, failures, errors, List.of());
  }

  public TestResult {
    errors = errors == null ? List.of() : List.copyOf(errors);
    allDurations = allDurations == null ? List.of() : List.copyOf(allDurations);
  }
}
