package io.llmcompactor.core;

import io.llmcompactor.core.extract.FixTargetGenerator;
import io.llmcompactor.core.git.GitDiffExtractor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class SummaryBuilder {

  private final List<BuildError> errors = new ArrayList<>();
  private final List<Double> allDurations = new ArrayList<>();
  private final List<SlowTest> allSlowTests = new ArrayList<>();
  private int testsRun;
  private int failures;
  private boolean buildFailed;
  private long sessionStartTime;
  private boolean showFixTargets = CompactorDefaults.SHOW_FIX_TARGETS;
  private boolean showRecentChanges = CompactorDefaults.SHOW_RECENT_CHANGES;
  private boolean showTotalDuration = CompactorDefaults.SHOW_TOTAL_DURATION;
  private boolean showDurationReport = CompactorDefaults.SHOW_DURATION_REPORT;
  private boolean showSlowTests = CompactorDefaults.SHOW_SLOW_TESTS;
  private double testDurationThresholdMs = CompactorDefaults.TEST_DURATION_THRESHOLD_MS;

  public SummaryBuilder addErrors(List<BuildError> e) {
    if (e != null) {
      errors.addAll(e);
    }
    return this;
  }

  public SummaryBuilder addDurations(List<Double> d) {
    if (d != null) {
      allDurations.addAll(d);
    }
    return this;
  }

  public SummaryBuilder addSlowTests(List<SlowTest> st) {
    if (st != null) {
      allSlowTests.addAll(st);
    }
    return this;
  }

  public SummaryBuilder withTestsRun(int n) {
    this.testsRun += n;
    return this;
  }

  public SummaryBuilder withFailures(int n) {
    this.failures += n;
    return this;
  }

  public SummaryBuilder withBuildFailed(boolean f) {
    if (f) {
      this.buildFailed = true;
    }
    return this;
  }

  public SummaryBuilder withSessionStartTime(long t) {
    this.sessionStartTime = t;
    return this;
  }

  public SummaryBuilder withShowFixTargets(boolean v) {
    this.showFixTargets = v;
    return this;
  }

  public SummaryBuilder withShowRecentChanges(boolean v) {
    this.showRecentChanges = v;
    return this;
  }

  public SummaryBuilder withShowTotalDuration(boolean v) {
    this.showTotalDuration = v;
    return this;
  }

  public SummaryBuilder withShowDurationReport(boolean v) {
    this.showDurationReport = v;
    return this;
  }

  public SummaryBuilder withShowSlowTests(boolean v) {
    this.showSlowTests = v;
    return this;
  }

  public SummaryBuilder withTestDurationThresholdMs(double v) {
    this.testDurationThresholdMs = v;
    return this;
  }

  public BuildSummary build() {
    List<BuildError> aggregated = BuildSummary.aggregateErrors(errors);
    List<FixTarget> targets =
        showFixTargets
            ? FixTargetGenerator.generate(aggregated)
            : Collections.<FixTarget>emptyList();
    List<String> recentChanges =
        showRecentChanges ? GitDiffExtractor.changedFiles() : Collections.<String>emptyList();
    Long totalDuration = showTotalDuration ? System.currentTimeMillis() - sessionStartTime : null;
    Map<String, Double> percentiles =
        showDurationReport && !allDurations.isEmpty()
            ? BuildSummary.computePercentiles(allDurations)
            : null;
    List<SlowTest> slowTests =
        showSlowTests
            ? BuildSummary.filterSlowTests(allSlowTests, testDurationThresholdMs)
            : Collections.<SlowTest>emptyList();
    String status = aggregated.isEmpty() && !buildFailed ? "SUCCESS" : "FAILED";
    return new BuildSummary(
        status,
        testsRun,
        failures,
        aggregated,
        targets,
        recentChanges,
        totalDuration,
        percentiles,
        slowTests);
  }
}
