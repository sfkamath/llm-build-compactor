package io.llmcompactor.core;

import java.util.List;

public record BuildError(
    String type,
    String file,
    List<Integer> lines,
    String message,
    String stackTrace,
    double testDuration) {
  public BuildError(String type, String file, int line, String message, String stackTrace) {
    this(type, file, List.of(line), message, stackTrace, 0.0);
  }

  public BuildError(
      String type, String file, int line, String message, String stackTrace, double testDuration) {
    this(type, file, List.of(line), message, stackTrace, testDuration);
  }
}
