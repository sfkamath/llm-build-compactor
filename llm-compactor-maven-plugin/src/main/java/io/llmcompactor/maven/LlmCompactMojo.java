package io.llmcompactor.maven;

import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.BuildSummary;
import io.llmcompactor.core.FixTarget;
import io.llmcompactor.core.SummaryWriter;
import io.llmcompactor.core.extract.FixTargetGenerator;
import io.llmcompactor.core.git.GitDiffExtractor;
import io.llmcompactor.core.parser.SurefireParser;
import io.llmcompactor.core.parser.TestResult;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "compact", defaultPhase = LifecyclePhase.VERIFY)
public class LlmCompactMojo extends AbstractMojo {
  private static final String EXTENSION_ACTIVE_PROPERTY = "llmCompactor.extension.active";

  @Parameter(property = "llmCompactor.enabled", defaultValue = "true")  // CompactorDefaults.ENABLED
  private boolean enabled;

  @Parameter(property = "llmCompactor.outputPath", defaultValue = "target/llm-summary.json")  // CompactorDefaults.OUTPUT_PATH
  private String outputPath;

  /** Output mode preset. Overrides individual flags when set. */
  public enum Mode {
    /** JSON output with fix targets, no logs (optimized for AI agents) */
    agent,
    /** JSON output with fix targets and test logs (for debugging) */
    debug,
    /** Human-readable output, no logs (default) */
    human
  }

  @Parameter(property = "llmCompactor.mode")
  private Mode mode;

  @Parameter(property = "llmCompactor.outputAsJson", defaultValue = "true")  // CompactorDefaults.OUTPUT_AS_JSON
  private boolean outputAsJson;

  @Parameter(property = "llmCompactor.compressStackFrames", defaultValue = "true")  // CompactorDefaults.COMPRESS_STACK_FRAMES
  private boolean compressStackFrames;

  @Parameter(property = "llmCompactor.showFixTargets", defaultValue = "true")  // CompactorDefaults.SHOW_FIX_TARGETS
  private boolean showFixTargets;

  @Parameter(property = "llmCompactor.showRecentChanges", defaultValue = "false")  // CompactorDefaults.SHOW_RECENT_CHANGES
  private boolean showRecentChanges;

  @Parameter(property = "llmCompactor.showSlowTests", defaultValue = "true")  // CompactorDefaults.SHOW_SLOW_TESTS
  private boolean showSlowTests;

  @Parameter(property = "llmCompactor.showTotalDuration", defaultValue = "false")  // CompactorDefaults.SHOW_TOTAL_DURATION
  private boolean showTotalDuration;

  @Parameter(property = "llmCompactor.showDurationReport", defaultValue = "false")  // CompactorDefaults.SHOW_DURATION_REPORT
  private boolean showDurationReport;

  @Parameter(property = "llmCompactor.includePackages")
  private String includePackages;

  @Parameter(property = "llmCompactor.showFailedTestLogs", defaultValue = "false")  // CompactorDefaults.SHOW_FAILED_TEST_LOGS
  private boolean showFailedTestLogs;

  @Parameter(property = "llmCompactor.testDurationThresholdMs", defaultValue = "100")  // CompactorDefaults.TEST_DURATION_THRESHOLD_MS
  private double testDurationThresholdMs;

  @Parameter(defaultValue = "${session}", readonly = true)
  private MavenSession session;

  @Parameter(defaultValue = "${project.build.directory}", readonly = true)
  private File buildDirectory;

  @Parameter(defaultValue = "${project.basedir}", readonly = true)
  private File basedir;

  public void execute() throws MojoExecutionException {

    if (!enabled) {
      return;
    }

    // Apply mode preset if specified (overrides individual flags)
    if (mode != null) {
      applyMode(mode);
    }

    // If BuildOutputSpy is active, it handles everything automatically via SessionEnded event.
    // This Mojo can be skipped when the extension is present to avoid double summaries.
    if (Boolean.getBoolean(EXTENSION_ACTIVE_PROPERTY)) {
      return;
    }

    List<String> includePackagesList =
        includePackages == null || includePackages.isEmpty()
            ? Collections.<String>emptyList()
            : Arrays.asList(includePackages.split(","));

    // Parse test results from existing reports
    long sessionStartTime =
        session != null && session.getStartTime() != null ? session.getStartTime().getTime() : 0L;
    Path targetDir = buildDirectory != null ? buildDirectory.toPath() : Paths.get("target");
    TestResult testResult =
        SurefireParser.parse(
            targetDir,
            compressStackFrames,
            includePackagesList,
            sessionStartTime,
            showFailedTestLogs);
    List<BuildError> testFailures = testResult.errors();
    List<Double> allDurations = testResult.allDurations();

    // Get compilation errors (currently empty, can be populated from EventSpy)
    List<BuildError> compileErrors = new ArrayList<>();

    List<BuildError> allErrors = new ArrayList<>();
    allErrors.addAll(compileErrors);
    allErrors.addAll(testFailures);

    allErrors = BuildSummary.aggregateErrors(allErrors);

    List<FixTarget> targets =
        showFixTargets
            ? FixTargetGenerator.generate(allErrors)
            : Collections.<FixTarget>emptyList();

    List<String> recentChanges =
        showRecentChanges ? GitDiffExtractor.changedFiles() : Collections.<String>emptyList();

    Long totalBuildDurationMs = null;
    if (showTotalDuration) {
      // Mojo doesn't have session start time, use 0 as fallback or estimate
      totalBuildDurationMs = 0L;
    }

    Map<String, Double> testDurationPercentiles = null;
    if (showDurationReport && !allDurations.isEmpty()) {
      Collections.sort(allDurations);
      testDurationPercentiles = new TreeMap<>();
      testDurationPercentiles.put("p50", allDurations.get((int) (allDurations.size() * 0.50)));
      testDurationPercentiles.put("p90", allDurations.get((int) (allDurations.size() * 0.90)));
      testDurationPercentiles.put("p95", allDurations.get((int) (allDurations.size() * 0.95)));
      testDurationPercentiles.put("p99", allDurations.get((int) (allDurations.size() * 0.99)));
      testDurationPercentiles.put("max", allDurations.get(allDurations.size() - 1));
    }

    BuildSummary summary =
        new BuildSummary(
            allErrors.isEmpty() ? "SUCCESS" : "FAILED",
            testResult.testsRun(),
            testResult.failures(),
            allErrors,
            targets,
            recentChanges,
            totalBuildDurationMs,
            testDurationPercentiles);

    Path resolvedOutputPath = Paths.get(outputPath);
    if (!resolvedOutputPath.isAbsolute() && basedir != null) {
      resolvedOutputPath = basedir.toPath().resolve(resolvedOutputPath);
    }
    SummaryWriter.write(summary, resolvedOutputPath);

    PrintStream out = System.out;

    if (outputAsJson) {
      out.print(SummaryWriter.toJson(summary, testDurationThresholdMs));
    } else {
      out.println(SummaryWriter.toHumanReadable(summary, showSlowTests, testDurationThresholdMs));
    }
  }

  private void applyMode(Mode mode) {
    switch (mode) {
      case agent:
        // JSON + fixTargets, no logs (optimized for AI agents)
        outputAsJson = true;
        showFixTargets = true;
        showFailedTestLogs = false;
        break;
      case debug:
        // JSON + fixTargets + logs (for debugging)
        outputAsJson = true;
        showFixTargets = true;
        showFailedTestLogs = true;
        break;
      case human:
        // Human-readable, no logs (default)
        outputAsJson = false;
        showFixTargets = true;
        showFailedTestLogs = false;
        break;
    }
  }
}
