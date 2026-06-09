package io.llmcompactor.gradle;

import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.BuildSummary;
import io.llmcompactor.core.CompactorConfig;
import io.llmcompactor.core.DefaultCompactorConfig;
import io.llmcompactor.core.PackageDiscoverer;
import io.llmcompactor.core.SummaryBuilder;
import io.llmcompactor.core.SummaryWriter;
import io.llmcompactor.core.extract.CompilationErrorExtractor;
import io.llmcompactor.core.parser.GradleParser;
import io.llmcompactor.core.parser.TestResultAggregator;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationCompletionListener;
import org.gradle.tooling.events.task.TaskFailureResult;
import org.gradle.tooling.events.task.TaskFinishEvent;

/**
 * Shared service that collects build events and log lines, emitting a compact summary upon build
 * completion.
 */
public abstract class CompletionService
    implements BuildService<CompletionService.Params>, OperationCompletionListener, AutoCloseable {

  /** Parameters for the completion service. */
  public interface Params extends BuildServiceParameters {
    /**
     * The start time of the build session in milliseconds.
     *
     * @return property containing the session start time
     */
    Property<Long> getSessionStartTime();
  }

  /** Constructs the completion service. */
  @Inject
  public CompletionService() {}

  private final List<CharSequence> logLines = Collections.synchronizedList(new ArrayList<>());
  private final AtomicBoolean buildFailed = new AtomicBoolean(false);

  private volatile PrintStream originalOut;
  private volatile PrintStream originalErr;
  private volatile Project rootProject;
  private volatile LlmCompactorPlugin.LlmCompactorExtension extension;

  StandardOutputListener listener() {
    return logLines::add;
  }

  void init(
      Project rootProject,
      LlmCompactorPlugin.LlmCompactorExtension extension,
      PrintStream originalOut,
      PrintStream originalErr) {
    this.rootProject = rootProject;
    this.extension = extension;
    this.originalOut = originalOut;
    this.originalErr = originalErr;
  }

  @Override
  public void onFinish(FinishEvent event) {
    if (event instanceof TaskFinishEvent && event.getResult() instanceof TaskFailureResult) {
      buildFailed.set(true);
      TaskFailureResult fr = (TaskFailureResult) event.getResult();
      synchronized (logLines) {
        for (org.gradle.tooling.Failure failure : fr.getFailures()) {
          if (failure.getMessage() != null) {
            logLines.add(failure.getMessage());
          }
          if (failure.getDescription() != null) {
            logLines.add(failure.getDescription());
          }
        }
      }
    }
  }

  @Override
  public void close() {
    System.setOut(originalOut);
    System.setErr(originalErr);
    emit(rootProject, extension, getParameters().getSessionStartTime().get());
  }

  private void emit(
      Project project, LlmCompactorPlugin.LlmCompactorExtension ext, long sessionStartTime) {
    CompactorConfig config = toConfig(ext).resolved();
    if (config == null || !config.enabled()) {
      return;
    }

    List<List<String>> scanResults = new ArrayList<>();
    for (Project p : project.getAllprojects()) {
      scanResults.add(scanProjectPackages(p));
    }
    List<String> whitelist =
        DefaultCompactorConfig.mergeWhitelist(config.stackFrameWhitelist(), scanResults);
    List<String> blacklist = config.stackFrameBlacklist();

    List<BuildError> compilationErrors;
    if (buildFailed.get()) {
      String fullOutput;
      synchronized (logLines) {
        fullOutput =
            logLines.stream()
                .map(line -> CompilationErrorExtractor.stripAnsi(line.toString()))
                .collect(Collectors.joining("\n"));
      }
      compilationErrors =
          new ArrayList<>(CompilationErrorExtractor.extractOrWrap(fullOutput, "build.gradle"));
    } else {
      compilationErrors = new ArrayList<>();
    }

    TestResultAggregator testResults = new TestResultAggregator();
    for (Project p : project.getAllprojects()) {
      try {
        Path testResultsDir =
            p.getLayout().getBuildDirectory().getAsFile().get().toPath().resolve("test-results");
        if (testResultsDir.toFile().exists()) {
          testResults.add(
              GradleParser.parse(
                  testResultsDir,
                  config.compressStackFrames(),
                  whitelist,
                  blacklist,
                  config.showFailedTestLogs()));
        }
      } catch (Exception e) {
        // Ignore
      }
    }

    List<BuildError> allErrors = new ArrayList<>();
    allErrors.addAll(compilationErrors);
    allErrors.addAll(testResults.errors());

    // Filter out generic task failures if we have higher-signal root causes (test errors or
    // non-generic compilation errors)
    boolean hasHighSignal =
        !testResults.errors().isEmpty()
            || allErrors.stream()
                .anyMatch(
                    e ->
                        !"build.gradle".equals(e.file())
                            && !e.message().startsWith("Execution failed for task"));

    if (hasHighSignal) {
      allErrors.removeIf(
          e ->
              "build.gradle".equals(e.file())
                  && e.message().startsWith("Execution failed for task"));
    }

    BuildSummary summary =
        new SummaryBuilder()
            .addErrors(allErrors)
            .addDurations(testResults.allDurations())
            .addSlowTests(testResults.slowTests())
            .withTestsRun(testResults.testsRun())
            .withFailures(testResults.failures())
            .withBuildFailed(buildFailed.get())
            .withSessionStartTime(sessionStartTime)
            .withConfig(config)
            .build();

    // Write default summary file
    Path buildDir = project.getLayout().getBuildDirectory().getAsFile().get().toPath();
    SummaryWriter.write(summary, buildDir.resolve("llm-summary.json"));

    // Write to custom path if specified
    if (config.outputPath() != null) {
      SummaryWriter.write(summary, project.getProjectDir().toPath().resolve(config.outputPath()));
    }

    String renderedSummary;
    if (config.outputAsJson()) {
      renderedSummary = SummaryWriter.toJson(summary, config.testDurationThresholdMs());
    } else {
      renderedSummary =
          SummaryWriter.toHumanReadable(
              summary, config.showSlowTests(), config.testDurationThresholdMs());
    }

    if (originalOut != null) {
      originalOut.println(renderedSummary);
      originalOut.flush();
    } else {
      rootProject.getLogger().quiet(renderedSummary);
    }

    if (originalErr != null && buildFailed.get() && compilationErrors.isEmpty()) {
      originalErr.println(
          "[LLM Compactor] Build failed but no compilation errors extracted. Log lines: "
              + logLines.size());
    }
  }

  static CompactorConfig toConfig(LlmCompactorPlugin.LlmCompactorExtension ext) {
    return DefaultCompactorConfig.builder()
        .enabled(Boolean.TRUE.equals(ext.getEnabled().get()))
        .outputPath(ext.getOutputPath().getOrNull())
        .mode(ext.getMode().getOrNull())
        .outputAsJson(ext.getOutputAsJson().get())
        .compressStackFrames(ext.getCompressStackFrames().get())
        .showFixTargets(ext.getShowFixTargets().get())
        .showRecentChanges(ext.getShowRecentChanges().get())
        .showSlowTests(Boolean.TRUE.equals(ext.getShowSlowTests().get()))
        .showTotalDuration(Boolean.TRUE.equals(ext.getShowTotalDuration().get()))
        .showDurationReport(Boolean.TRUE.equals(ext.getShowDurationReport().get()))
        .showFailedTestLogs(ext.getShowFailedTestLogs().get())
        .testDurationThresholdMs(ext.getTestDurationThresholdMs().get())
        .stackFrameWhitelist(ext.getStackFrameWhitelist().getOrElse(Collections.emptyList()))
        .stackFrameBlacklist(ext.getStackFrameBlacklist().getOrElse(Collections.emptyList()))
        .build();
  }

  private List<String> scanProjectPackages(Project p) {
    List<String> packages = new ArrayList<>();
    File mainSrc = p.file("src/main/java");
    if (mainSrc.exists()) {
      packages.addAll(
          PackageDiscoverer.discoverPackages(Collections.singletonList(mainSrc.toPath())));
    }
    File testSrc = p.file("src/test/java");
    if (testSrc.exists()) {
      packages.addAll(
          PackageDiscoverer.discoverPackages(Collections.singletonList(testSrc.toPath())));
    }
    return packages;
  }
}
