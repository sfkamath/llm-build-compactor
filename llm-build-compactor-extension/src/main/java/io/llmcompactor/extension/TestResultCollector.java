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
import io.llmcompactor.core.SlowTest;
import io.llmcompactor.core.parser.ParserUtils;
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

  // -------------------------------------------------------------------------
  // Collection
  // -------------------------------------------------------------------------

  void collectFrom(MavenSession session, OutputConfig config, long sessionStartTime) {
    List<MavenProject> projects = session.getProjects();
    if (projects == null) {
      return;
    }
    List<String> whitelist = buildStackFrameWhitelist(session, config);
    List<String> blacklist = buildStackFrameBlacklist(session);
    for (MavenProject project : projects) {
      Path targetDir = project.getBasedir().toPath().resolve("target");
      if (Files.exists(targetDir)) {
        aggregator.add(
            SurefireParser.parse(
                targetDir,
                config.compress,
                whitelist,
                blacklist,
                sessionStartTime,
                config.showFailedTestLogs));
      }
    }
  }

  // -------------------------------------------------------------------------
  // Accessors
  // -------------------------------------------------------------------------

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

  private static List<String> buildStackFrameWhitelist(MavenSession session, OutputConfig config) {
    // Whitelist is seeded from the config property, then augmented with packages
    // discovered by scanning each project's source roots.
    PropertyResolver resolver = resolverFor(session);
    String raw = resolver.getString("llmCompactor.stackFrameWhitelist", "");
    List<String> packages = new ArrayList<>(ParserUtils.splitCsv(raw));

    List<MavenProject> projects = session.getProjects();
    if (projects != null) {
      for (MavenProject project : projects) {
        packages.addAll(PackageScanner.scan(project));
      }
    }
    return packages;
  }

  private static List<String> buildStackFrameBlacklist(MavenSession session) {
    String raw = resolverFor(session).getString("llmCompactor.stackFrameBlacklist", "");
    return ParserUtils.splitCsv(raw);
  }

  /** Thin helper so this class does not need its own session field for the whitelist build. */
  private static PropertyResolver resolverFor(MavenSession session) {
    MavenProject top = session.getTopLevelProject();
    return new PropertyResolver(session, top != null ? top.getProperties() : null);
  }
}
