package io.llmcompactor.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class BuildSummary {
  private final String status;
  private final int testsRun;
  private final int failures;
  private final List<BuildError> errors;
  private final List<FixTarget> fixTargets;
  private final List<String> recentChanges;
  private final Long totalBuildDurationMs;
  private final Map<String, Double> testDurationPercentiles;

  public BuildSummary(
      String status,
      int testsRun,
      int failures,
      List<BuildError> errors,
      List<FixTarget> fixTargets,
      List<String> recentChanges,
      Long totalBuildDurationMs,
      Map<String, Double> testDurationPercentiles) {
    this.status = status;
    this.testsRun = testsRun;
    this.failures = failures;
    this.errors = aggregateErrors(errors);
    this.fixTargets =
        fixTargets != null
            ? Collections.unmodifiableList(fixTargets)
            : Collections.<FixTarget>emptyList();
    this.recentChanges =
        recentChanges != null
            ? Collections.unmodifiableList(recentChanges)
            : Collections.<String>emptyList();
    this.totalBuildDurationMs = totalBuildDurationMs;
    this.testDurationPercentiles =
        testDurationPercentiles != null
            ? Collections.unmodifiableMap(new TreeMap<String, Double>(testDurationPercentiles))
            : null;
  }

  public BuildSummary(
      String status,
      int testsRun,
      int failures,
      List<BuildError> errors,
      List<FixTarget> fixTargets,
      List<String> recentChanges) {
    this(status, testsRun, failures, errors, fixTargets, recentChanges, null, null);
  }

  public String status() {
    return status;
  }

  public int testsRun() {
    return testsRun;
  }

  public int failures() {
    return failures;
  }

  public List<BuildError> errors() {
    return Collections.unmodifiableList(errors);
  }

  public List<FixTarget> fixTargets() {
    return fixTargets;
  }

  public List<String> recentChanges() {
    return recentChanges;
  }

  public Long totalBuildDurationMs() {
    return totalBuildDurationMs;
  }

  public Map<String, Double> testDurationPercentiles() {
    return testDurationPercentiles;
  }

  // Jackson getters
  public String getStatus() {
    return status;
  }

  public int getTestsRun() {
    return testsRun;
  }

  public int getFailures() {
    return failures;
  }

  public List<BuildError> getErrors() {
    return Collections.unmodifiableList(errors);
  }

  public List<FixTarget> getFixTargets() {
    return fixTargets;
  }

  public List<String> getRecentChanges() {
    return recentChanges;
  }

  public Long getTotalBuildDurationMs() {
    return totalBuildDurationMs;
  }

  public Map<String, Double> getTestDurationPercentiles() {
    return testDurationPercentiles;
  }

  public static List<BuildError> aggregateErrors(List<BuildError> rawErrors) {
    if (rawErrors == null || rawErrors.isEmpty()) {
      return Collections.emptyList();
    }

    Map<ErrorGroup, List<Integer>> grouped = new LinkedHashMap<>();
    for (BuildError e : rawErrors) {
      ErrorGroup group =
          new ErrorGroup(e.type(), e.file(), e.message(), e.stackTrace(), e.testDuration());
      List<Integer> lines = grouped.get(group);
      if (lines == null) {
        lines = new ArrayList<>();
        grouped.put(group, lines);
      }
      if (e.lines() != null) {
        for (Integer line : e.lines()) {
          if (!lines.contains(line)) {
            lines.add(line);
          }
        }
      }
    }

    List<BuildError> result = new ArrayList<>();
    for (Map.Entry<ErrorGroup, List<Integer>> entry : grouped.entrySet()) {
      ErrorGroup g = entry.getKey();
      List<Integer> lines = entry.getValue();
      Collections.sort(lines);
      result.add(new BuildError(g.type, g.file, lines, g.message, g.stackTrace, g.testDuration));
    }
    return Collections.unmodifiableList(result);
  }

  private static class ErrorGroup {
    private final String type;
    private final String file;
    private final String message;
    private final String stackTrace;
    private final double testDuration;

    ErrorGroup(String type, String file, String message, String stackTrace, double testDuration) {
      this.type = type;
      this.file = file;
      this.message = message;
      this.stackTrace = stackTrace;
      this.testDuration = testDuration;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ErrorGroup that = (ErrorGroup) o;
      return Double.compare(that.testDuration, testDuration) == 0
          && Objects.equals(type, that.type)
          && Objects.equals(file, that.file)
          && Objects.equals(message, that.message)
          && Objects.equals(stackTrace, that.stackTrace);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, file, message, stackTrace, testDuration);
    }
  }
}
