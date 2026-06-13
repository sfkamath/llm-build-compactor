package io.llmcompactor.maven;

import io.llmcompactor.core.BuildSummary;
import io.llmcompactor.core.CompactorConfig;
import io.llmcompactor.core.CompactorDefaults;
import io.llmcompactor.core.DefaultCompactorConfig;
import io.llmcompactor.core.SummaryBuilder;
import io.llmcompactor.core.SummaryWriter;
import io.llmcompactor.core.parser.ParserUtils;
import io.llmcompactor.core.parser.SurefireParser;
import io.llmcompactor.core.parser.TestResult;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
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

  @Parameter(property = "llmCompactor.enabled", defaultValue = "true")
  private boolean enabled = CompactorConfig.DEFAULT_ENABLED;

  @Parameter(
      property = "llmCompactor.outputPath",
      defaultValue = "target/llm-summary.json") // CompactorConfig.OUTPUT_PATH
  private String outputPath;

  @Parameter(property = "llmCompactor.mode")
  private String mode;

  @Parameter(
      property = "llmCompactor.outputAsJson",
      defaultValue = "true") // CompactorConfig.DEFAULT_OUTPUT_AS_JSON
  private boolean outputAsJson = CompactorConfig.DEFAULT_OUTPUT_AS_JSON;

  @Parameter(
      property = "llmCompactor.compressStackFrames",
      defaultValue = "true") // CompactorConfig.DEFAULT_COMPRESS_STACK_FRAMES
  private boolean compressStackFrames = CompactorConfig.DEFAULT_COMPRESS_STACK_FRAMES;

  @Parameter(
      property = "llmCompactor.showFixTargets",
      defaultValue = "true") // CompactorConfig.DEFAULT_SHOW_FIX_TARGETS
  private boolean showFixTargets = CompactorConfig.DEFAULT_SHOW_FIX_TARGETS;

  @Parameter(
      property = "llmCompactor.showRecentChanges",
      defaultValue = "false") // CompactorConfig.DEFAULT_SHOW_RECENT_CHANGES
  private boolean showRecentChanges = CompactorConfig.DEFAULT_SHOW_RECENT_CHANGES;

  @Parameter(
      property = "llmCompactor.showSlowTests",
      defaultValue = "true") // CompactorConfig.DEFAULT_SHOW_SLOW_TESTS
  private boolean showSlowTests = CompactorConfig.DEFAULT_SHOW_SLOW_TESTS;

  @Parameter(
      property = "llmCompactor.showTotalDuration",
      defaultValue = "false") // CompactorConfig.DEFAULT_SHOW_TOTAL_DURATION
  private boolean showTotalDuration = CompactorConfig.DEFAULT_SHOW_TOTAL_DURATION;

  @Parameter(
      property = "llmCompactor.showDurationReport",
      defaultValue = "false") // CompactorConfig.DEFAULT_SHOW_DURATION_REPORT
  private boolean showDurationReport = CompactorConfig.DEFAULT_SHOW_DURATION_REPORT;

  @Parameter(property = "llmCompactor.stackFrameWhitelist")
  private String stackFrameWhitelist;

  @Parameter(property = "llmCompactor.stackFrameBlacklist")
  private String stackFrameBlacklist;

  @Parameter(
      property = "llmCompactor.showFailedTestLogs",
      defaultValue = "true") // CompactorConfig.DEFAULT_SHOW_FAILED_TEST_LOGS
  private boolean showFailedTestLogs = CompactorConfig.DEFAULT_SHOW_FAILED_TEST_LOGS;

  @Parameter(
      property = "llmCompactor.testDurationThresholdMs",
      defaultValue = "100") // CompactorConfig.DEFAULT_TEST_DURATION_THRESHOLD_MS
  private double testDurationThresholdMs = CompactorConfig.DEFAULT_TEST_DURATION_THRESHOLD_MS;

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

    // If BuildOutputSpy is active, it handles everything automatically via SessionEnded event.
    // This Mojo can be skipped when the extension is present to avoid double summaries.
    if (Boolean.getBoolean(EXTENSION_ACTIVE_PROPERTY)) {
      return;
    }

    CompactorConfig config =
        DefaultCompactorConfig.builder()
            .enabled(CompactorDefaults.resolveEnabled(llmcePresent, enabledValue))
            .outputPath(outputPath)
            .mode(mode)
            .outputAsJson(outputAsJson)
            .compressStackFrames(compressStackFrames)
            .showFixTargets(showFixTargets)
            .showRecentChanges(showRecentChanges)
            .showSlowTests(showSlowTests)
            .showTotalDuration(showTotalDuration)
            .showDurationReport(showDurationReport)
            .showFailedTestLogs(showFailedTestLogs)
            .testDurationThresholdMs(testDurationThresholdMs)
            .stackFrameWhitelist(ParserUtils.splitCsv(stackFrameWhitelist))
            .stackFrameBlacklist(ParserUtils.splitCsv(stackFrameBlacklist))
            .build()
            .resolved();

    long sessionStartTime =
        session != null && session.getStartTime() != null ? session.getStartTime().getTime() : 0L;
    Path targetDir = buildDirectory != null ? buildDirectory.toPath() : Paths.get("target");
    TestResult testResult =
        SurefireParser.parse(
            targetDir,
            config.compressStackFrames(),
            config.stackFrameWhitelist(),
            config.stackFrameBlacklist(),
            sessionStartTime,
            config.showFailedTestLogs());

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
            .withConfig(config)
            .build();

    Path resolvedOutputPath = Paths.get(config.outputPath());
    if (!resolvedOutputPath.isAbsolute() && basedir != null) {
      resolvedOutputPath = basedir.toPath().resolve(resolvedOutputPath);
    }
    SummaryWriter.write(summary, resolvedOutputPath);

    PrintStream out = System.out;
    if (config.outputAsJson()) {
      out.print(SummaryWriter.toJson(summary, config.testDurationThresholdMs()));
    } else {
      out.println(
          SummaryWriter.toHumanReadable(
              summary, config.showSlowTests(), config.testDurationThresholdMs()));
    }
  }
}
