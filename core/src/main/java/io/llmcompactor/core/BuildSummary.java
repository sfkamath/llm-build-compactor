package io.llmcompactor.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public record BuildSummary(
    String status,
    int testsRun,
    int failures,
    List<BuildError> errors,
    List<FixTarget> fixTargets,
    List<String> recentChanges,
    @JsonInclude(JsonInclude.Include.NON_NULL) Long totalBuildDurationMs,
    @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Double> testDurationPercentiles) {

  public BuildSummary(
      String status,
      int testsRun,
      int failures,
      List<BuildError> errors,
      List<FixTarget> fixTargets,
      List<String> recentChanges) {
    this(status, testsRun, failures, errors, fixTargets, recentChanges, null, null);
  }

  public BuildSummary {
    errors = aggregateErrors(errors);
    fixTargets = fixTargets == null ? List.of() : List.copyOf(fixTargets);
    recentChanges = recentChanges == null ? List.of() : List.copyOf(recentChanges);
    testDurationPercentiles =
        testDurationPercentiles == null
            ? null
            : Collections.unmodifiableMap(new TreeMap<>(testDurationPercentiles));
  }

  public static List<BuildError> aggregateErrors(List<BuildError> rawErrors) {
    if (rawErrors == null || rawErrors.isEmpty()) {
      return List.of();
    }

    Map<ErrorGroup, List<Integer>> grouped = new LinkedHashMap<>();
    for (BuildError e : rawErrors) {
      ErrorGroup group =
          new ErrorGroup(e.type(), e.file(), e.message(), e.stackTrace(), e.testDuration());
      List<Integer> lines = grouped.computeIfAbsent(group, k -> new ArrayList<>());
      if (e.lines() != null) {
        for (Integer line : e.lines()) {
          if (!lines.contains(line)) {
            lines.add(line);
          }
        }
      }
    }

    return grouped.entrySet().stream()
        .map(
            entry -> {
              ErrorGroup g = entry.getKey();
              List<Integer> lines = entry.getValue();
              Collections.sort(lines);
              return new BuildError(
                  g.type(), g.file(), lines, g.message(), g.stackTrace(), g.testDuration());
            })
        .collect(Collectors.toList());
  }

  private record ErrorGroup(
      String type, String file, String message, String stackTrace, double testDuration) {
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
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
