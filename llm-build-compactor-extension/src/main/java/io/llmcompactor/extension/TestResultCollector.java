/*
 * Copyright 2024 Jaromir Hamala (jerrinot)
 * Copyright 2024 LLM Build Compactor Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.llmcompactor.extension;

import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.CompactorConfig;
import io.llmcompactor.core.DefaultCompactorConfig;
import io.llmcompactor.core.SlowTest;
import io.llmcompactor.core.parser.SurefireParser;
import io.llmcompactor.core.parser.TestResultAggregator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/** Aggregates Surefire test results across all projects in a Maven session. */
final class TestResultCollector {

  private final TestResultAggregator aggregator = new TestResultAggregator();

  void collectFrom(MavenSession session, CompactorConfig config, long sessionStartTime) {
    List<MavenProject> projects = session.getProjects();
    if (projects == null) {
      return;
    }
    List<String> whitelist = buildWhitelist(session, config);
    for (MavenProject project : projects) {
      Path targetDir = project.getBasedir().toPath().resolve("target");
      if (Files.exists(targetDir)) {
        aggregator.add(
            SurefireParser.parse(
                targetDir,
                config.compressStackFrames(),
                whitelist,
                config.stackFrameBlacklist(),
                sessionStartTime,
                config.showFailedTestLogs()));
      }
    }
  }

  int testsRun() {
    return aggregator.testsRun();
  }

  int failures() {
    return aggregator.failures();
  }

  List<BuildError> errors() {
    return aggregator.errors();
  }

  List<Double> durations() {
    return aggregator.allDurations();
  }

  List<SlowTest> slowTests() {
    return aggregator.slowTests();
  }

  private static List<String> buildWhitelist(MavenSession session, CompactorConfig config) {
    List<MavenProject> projects = session.getProjects();
    List<List<String>> scanResults = new ArrayList<>();
    if (projects != null) {
      for (MavenProject project : projects) {
        scanResults.add(PackageScanner.scan(project));
      }
    }
    return DefaultCompactorConfig.mergeWhitelist(config.stackFrameWhitelist(), scanResults);
  }
}
