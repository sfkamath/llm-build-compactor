package io.llmcompactor.core.parser;

import io.llmcompactor.core.BuildError;
import java.util.List;

public record TestResult(
        int testsRun,
        int failures,
        List<BuildError> errors
) {}
