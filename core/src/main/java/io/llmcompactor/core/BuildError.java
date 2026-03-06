package io.llmcompactor.core;

public record BuildError(
    String type, 
    String file, 
    int line, 
    String message,
    String stackTrace
) {}
