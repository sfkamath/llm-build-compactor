package io.llmcompactor.core;

public record BuildError(
    String type, String file, int line, String message, String stackTrace, double testDuration) {
  public BuildError(String type, String file, int line, String message, String stackTrace) {
    this(type, file, line, message, stackTrace, 0.0);
  }
}
