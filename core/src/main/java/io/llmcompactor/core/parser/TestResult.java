package io.llmcompactor.core.parser;

import io.llmcompactor.core.BuildError;
import java.util.List;

public record TestResult(int testsRun, int failures, List<BuildError> errors) {
  public TestResult {
    errors = errors == null ? List.of() : List.copyOf(errors);
  }
}
