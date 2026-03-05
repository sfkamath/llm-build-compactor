package io.llmcompactor.core;

public record FixTarget(String file, int line, String reason, String snippet) {}
