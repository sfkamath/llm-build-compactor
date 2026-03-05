package io.llmcompactor.core;

import java.util.List;

public record BuildSummary(
        String status,
        int testsRun,
        int failures,
        List<BuildError> errors,
        List<FixTarget> fixTargets,
        List<String> recentChanges
) {}
