package io.llmcompactor.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
    errors = errors == null ? List.of() : List.copyOf(new LinkedHashSet<>(errors));
    fixTargets = fixTargets == null ? List.of() : List.copyOf(fixTargets);
    recentChanges = recentChanges == null ? List.of() : List.copyOf(recentChanges);
    testDurationPercentiles =
        testDurationPercentiles == null
            ? null
            : Collections.unmodifiableMap(new TreeMap<>(testDurationPercentiles));
  }
}
