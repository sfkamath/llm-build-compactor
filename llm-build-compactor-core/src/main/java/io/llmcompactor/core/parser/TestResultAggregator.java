package io.llmcompactor.core.parser;

import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.SlowTest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TestResultAggregator {

  private int totalTestsRun;
  private int totalFailures;
  private final List<BuildError> allErrors = new ArrayList<>();
  private final List<Double> allDurations = new ArrayList<>();
  private final List<SlowTest> allSlowTests = new ArrayList<>();

  public void add(TestResult result) {
    if (result == null) {
      return;
    }
    totalTestsRun += result.testsRun();
    totalFailures += result.failures();
    allErrors.addAll(result.errors());
    allDurations.addAll(result.allDurations());
    allSlowTests.addAll(result.slowTests());
  }

  public int testsRun() {
    return totalTestsRun;
  }

  public int failures() {
    return totalFailures;
  }

  public List<BuildError> errors() {
    return Collections.unmodifiableList(allErrors);
  }

  public List<Double> allDurations() {
    return Collections.unmodifiableList(allDurations);
  }

  public List<SlowTest> slowTests() {
    return Collections.unmodifiableList(allSlowTests);
  }
}
