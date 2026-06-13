package io.llmcompactor.core;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class BuildSummary {
  private final String status;
  private final int testsRun;
  private final int failures;
  private final List<BuildError> errors;

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private final List<FixTarget> fixTargets;

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private final List<String> recentChanges;

  private final Long totalBuildDurationMs;
  private final Map<String, Double> testDurationPercentiles;

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private final List<SlowTest> slowTests;

  public BuildSummary(
      String status,
      int testsRun,
      int failures,
      List<BuildError> errors,
      List<FixTarget> fixTargets,
      List<String> recentChanges,
      Long totalBuildDurationMs,
      Map<String, Double> testDurationPercentiles,
      List<SlowTest> slowTests) {
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
            ? Collections.unmodifiableMap(new TreeMap<>(testDurationPercentiles))
            : null;
    this.slowTests =
        slowTests != null
            ? Collections.unmodifiableList(slowTests)
            : Collections.<SlowTest>emptyList();
  }

  public BuildSummary(
      String status,
      int testsRun,
      int failures,
      List<BuildError> errors,
      List<FixTarget> fixTargets,
      List<String> recentChanges,
      Long totalBuildDurationMs,
      Map<String, Double> testDurationPercentiles) {
    this(
        status,
        testsRun,
        failures,
        errors,
        fixTargets,
        recentChanges,
        totalBuildDurationMs,
        testDurationPercentiles,
        Collections.emptyList());
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

  /** Computes test duration percentiles (p50, p90, p95, p99, max) from a list of durations. */
  public static Map<String, Double> computePercentiles(List<Double> durations) {
    if (durations == null || durations.isEmpty()) {
      return null;
    }
    List<Double> sorted = new ArrayList<>(durations);
    Collections.sort(sorted);
    Map<String, Double> percentiles = new TreeMap<>();
    percentiles.put("p50", sorted.get(sorted.size() * 50 / 100));
    percentiles.put("p90", sorted.get(sorted.size() * 90 / 100));
    percentiles.put("p95", sorted.get(sorted.size() * 95 / 100));
    percentiles.put("p99", sorted.get(sorted.size() * 99 / 100));
    percentiles.put("max", sorted.get(sorted.size() - 1));
    return percentiles;
  }

  public static List<SlowTest> filterSlowTests(List<SlowTest> tests, double thresholdMs) {
    if (tests == null) {
      return Collections.emptyList();
    }
    List<SlowTest> result = new ArrayList<>();
    for (SlowTest e : tests) {
      if (e.testDuration() >= thresholdMs) {
        result.add(e);
      }
    }
    return result;
  }

  public static List<BuildError> aggregateErrors(List<BuildError> rawErrors) {
    if (rawErrors == null || rawErrors.isEmpty()) {
      return Collections.emptyList();
    }

    Map<ErrorGroup, List<Integer>> grouped = new LinkedHashMap<>();
    Map<ErrorGroup, String> testLogsMap = new HashMap<>();
    for (BuildError e : rawErrors) {
      ErrorGroup group =
          new ErrorGroup(e.type(), e.file(), e.message(), e.stackTrace(), e.testDuration());
      List<Integer> lines = grouped.get(group);
      if (lines == null) {
        lines = new ArrayList<>();
        grouped.put(group, lines);
        // Preserve testLogs from the first error in the group
        if (e.testLogs() != null && !e.testLogs().isEmpty()) {
          testLogsMap.put(group, e.testLogs());
        }
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
      String testLogs = testLogsMap.get(g);
      result.add(
          new BuildError(g.type, g.file, lines, g.message, g.stackTrace, g.testDuration, testLogs));
    }
    return Collections.unmodifiableList(result);
  }

  @Value
  private static class ErrorGroup {
    String type;
    String file;
    String message;
    String stackTrace;
    double testDuration;
  }
}
