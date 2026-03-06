package io.llmcompactor.core;

import java.util.List;

public record BuildSummary(
    String status,
    int testsRun,
    int failures,
    List<BuildError> errors,
    List<FixTarget> fixTargets,
    List<String> recentChanges) {
  public BuildSummary {
    errors = errors == null ? List.of() : List.copyOf(errors);
    fixTargets = fixTargets == null ? List.of() : List.copyOf(fixTargets);
    recentChanges = recentChanges == null ? List.of() : List.copyOf(recentChanges);
  }
}
