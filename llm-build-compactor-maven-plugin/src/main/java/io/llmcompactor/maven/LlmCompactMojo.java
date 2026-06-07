package io.llmcompactor.maven;

import io.llmcompactor.core.BuildSummary;
import io.llmcompactor.core.CompactorDefaults;
import io.llmcompactor.core.ModePreset;
import io.llmcompactor.core.SummaryBuilder;
import io.llmcompactor.core.SummaryWriter;
import io.llmcompactor.core.parser.ParserUtils;
import io.llmcompactor.core.parser.SurefireParser;
import io.llmcompactor.core.parser.TestResult;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "compact", defaultPhase = LifecyclePhase.VERIFY)
public class LlmCompactMojo extends AbstractMojo {
  private static final String EXTENSION_ACTIVE_PROPERTY = "llmCompactor.extension.active";

  @Parameter(property = "llmCompactor.enabled", defaultValue = "true") // CompactorDefaults.ENABLED
  private boolean enabled;

  @Parameter(
      property = "llmCompactor.outputPath",
      defaultValue = "target/llm-summary.json") // CompactorDefaults.OUTPUT_PATH
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

  @Parameter(
      property = "llmCompactor.outputAsJson",
      defaultValue = "true") // CompactorDefaults.OUTPUT_AS_JSON
  private boolean outputAsJson;

  @Parameter(
      property = "llmCompactor.compressStackFrames",
      defaultValue = "true") // CompactorDefaults.COMPRESS_STACK_FRAMES
  private boolean compressStackFrames;

  @Parameter(
      property = "llmCompactor.showFixTargets",
      defaultValue = "true") // CompactorDefaults.SHOW_FIX_TARGETS
  private boolean showFixTargets;

  @Parameter(
      property = "llmCompactor.showRecentChanges",
      defaultValue = "false") // CompactorDefaults.SHOW_RECENT_CHANGES
  private boolean showRecentChanges;

  @Parameter(
      property = "llmCompactor.showSlowTests",
      defaultValue = "true") // CompactorDefaults.SHOW_SLOW_TESTS
  private boolean showSlowTests;

  @Parameter(
      property = "llmCompactor.showTotalDuration",
      defaultValue = "false") // CompactorDefaults.SHOW_TOTAL_DURATION
  private boolean showTotalDuration;

  @Parameter(
      property = "llmCompactor.showDurationReport",
      defaultValue = "false") // CompactorDefaults.SHOW_DURATION_REPORT
  private boolean showDurationReport;

  @Parameter(property = "llmCompactor.stackFrameWhitelist")
  private String stackFrameWhitelist;

  @Parameter(property = "llmCompactor.stackFrameBlacklist")
  private String stackFrameBlacklist;

  @Parameter(
      property = "llmCompactor.showFailedTestLogs",
      defaultValue = "false") // CompactorDefaults.SHOW_FAILED_TEST_LOGS
  private boolean showFailedTestLogs;

  @Parameter(
      property = "llmCompactor.testDurationThresholdMs",
      defaultValue = "100") // CompactorDefaults.TEST_DURATION_THRESHOLD_MS
  private double testDurationThresholdMs;

  @Parameter(defaultValue = "${session}", readonly = true)
  private MavenSession session;

  @Parameter(defaultValue = "${project.build.directory}", readonly = true)
  private File buildDirectory;

  @Parameter(defaultValue = "${project.basedir}", readonly = true)
  private File basedir;

  public void execute() throws MojoExecutionException {

    // @Parameter already applied system/user props to `enabled`; supplement with project
    // properties which @Parameter does not read (e.g. <llmCompactor.enabled> in pom.xml)
    Properties projectProps =
        session != null && session.getCurrentProject() != null
            ? session.getCurrentProject().getProperties()
            : new Properties();
    boolean llmcePresent =
        (session != null && session.getUserProperties().getProperty("llmce") != null)
            || System.getProperty("llmce") != null
            || projectProps.getProperty("llmce") != null;
    String enabledValue =
        projectProps.containsKey("llmCompactor.enabled")
            ? projectProps.getProperty("llmCompactor.enabled")
            : String.valueOf(enabled);
    if (!CompactorDefaults.resolveEnabled(llmcePresent, enabledValue)) {
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

    List<String> stackFrameWhitelistList = ParserUtils.splitCsv(stackFrameWhitelist);
    List<String> stackFrameBlacklistList = ParserUtils.splitCsv(stackFrameBlacklist);

    long sessionStartTime =
        session != null && session.getStartTime() != null ? session.getStartTime().getTime() : 0L;
    Path targetDir = buildDirectory != null ? buildDirectory.toPath() : Paths.get("target");
    TestResult testResult =
        SurefireParser.parse(
            targetDir,
            compressStackFrames,
            stackFrameWhitelistList,
            stackFrameBlacklistList,
            sessionStartTime,
            showFailedTestLogs);

    boolean sessionHasErrors =
        session != null && session.getResult() != null && session.getResult().hasExceptions();
    BuildSummary summary =
        new SummaryBuilder()
            .addErrors(testResult.errors())
            .addDurations(testResult.allDurations())
            .addSlowTests(testResult.slowTests())
            .withTestsRun(testResult.testsRun())
            .withFailures(testResult.failures())
            .withBuildFailed(sessionHasErrors)
            .withSessionStartTime(sessionStartTime)
            .withShowFixTargets(showFixTargets)
            .withShowRecentChanges(showRecentChanges)
            .withShowTotalDuration(showTotalDuration)
            .withShowDurationReport(showDurationReport)
            .withShowSlowTests(showSlowTests)
            .withTestDurationThresholdMs(testDurationThresholdMs)
            .build();

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
    ModePreset preset = ModePreset.from(mode.name());
    outputAsJson = preset.overrideOutputAsJson(outputAsJson);
    showFixTargets = preset.overrideShowFixTargets(showFixTargets);
    showFailedTestLogs = preset.overrideShowFailedTestLogs(showFailedTestLogs);
  }
}
