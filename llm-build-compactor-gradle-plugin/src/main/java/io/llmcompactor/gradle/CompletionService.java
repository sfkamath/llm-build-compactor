package io.llmcompactor.gradle;

import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.BuildSummary;
import io.llmcompactor.core.CompactorConfig;
import io.llmcompactor.core.CompactorDefaults;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.provider.ListProperty;
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
     * Gets the session start time.
     *
     * @return property containing the session start time
     */
    Property<Long> getSessionStartTime();

    /**
     * Gets the project root directory.
     *
     * @return property containing the project root directory
     */
    Property<File> getRootDir();

    /**
     * Gets the build directory.
     *
     * @return property containing the build directory
     */
    Property<File> getBuildDir();

    /**
     * Gets all build directories.
     *
     * @return list of build directories for all subprojects
     */
    ListProperty<File> getAllBuildDirs();

    /**
     * Gets all source directories.
     *
     * @return list of source directories for package discovery
     */
    ListProperty<File> getAllSourceDirs();

    /**
     * Gets the enabled status.
     *
     * @return whether the plugin is enabled
     */
    Property<Boolean> getEnabled();

    /**
     * Gets the JSON output status.
     *
     * @return whether to output as JSON
     */
    Property<Boolean> getOutputAsJson();

    /**
     * Gets the stack frame compression status.
     *
     * @return whether to compress stack traces
     */
    Property<Boolean> getCompressStackFrames();

    /**
     * Gets the stack frame whitelist.
     *
     * @return whitelist for stack traces
     */
    ListProperty<String> getStackFrameWhitelist();

    /**
     * Gets the stack frame blacklist.
     *
     * @return blacklist for stack traces
     */
    ListProperty<String> getStackFrameBlacklist();

    /**
     * Gets the slow tests display status.
     *
     * @return whether to show slow tests
     */
    Property<Boolean> getShowSlowTests();

    /**
     * Gets the test duration threshold.
     *
     * @return test duration threshold
     */
    Property<Double> getTestDurationThresholdMs();

    /**
     * Gets the custom output path.
     *
     * @return custom output path
     */
    Property<String> getOutputPath();

    /**
     * Gets the failed test logs display status.
     *
     * @return whether to show failed test logs
     */
    Property<Boolean> getShowFailedTestLogs();

    /**
     * Gets the fix targets display status.
     *
     * @return whether to show fix targets
     */
    Property<Boolean> getShowFixTargets();

    /**
     * Gets the recent changes display status.
     *
     * @return whether to show recent changes
     */
    Property<Boolean> getShowRecentChanges();

    /**
     * Gets the total duration display status.
     *
     * @return whether to show total duration
     */
    Property<Boolean> getShowTotalDuration();

    /**
     * Gets the duration report display status.
     *
     * @return whether to show duration report
     */
    Property<Boolean> getShowDurationReport();

    /**
     * Gets the mode preset.
     *
     * @return mode preset
     */
    Property<String> getMode();
  }

  /** Constructs the completion service. */
  @Inject
  public CompletionService() {}

  static volatile PrintStream originalOut;
  static volatile PrintStream originalErr;

  /**
   * The single null stream installed by suppression. Shared (not re-created per build) so capture
   * can reliably tell "the streams are still our null redirect" apart from a real console stream.
   */
  private static volatile PrintStream nullSentinel;

  /** The pending late-restore from the current/previous build, so a new build can cancel it. */
  private static volatile ScheduledFuture<?> pendingRestore;

  /** Returns the shared null sentinel, creating it on first use. */
  static synchronized PrintStream nullStream() {
    if (nullSentinel == null) {
      nullSentinel = CompactorDefaults.nullPrintStream();
    }
    return nullSentinel;
  }

  /**
   * Captures the real System.out/err exactly once per daemon lifetime, tolerating daemon reuse. If
   * a previous build nulled the streams and its late-restore has not fired yet, System.out is our
   * sentinel — we keep the originals captured earlier rather than capturing the null stream as the
   * "original". Also cancels any pending restore so it cannot clobber this build's redirect.
   */
  static synchronized void captureOriginals() {
    if (pendingRestore != null) {
      pendingRestore.cancel(false);
      pendingRestore = null;
    }
    PrintStream out = System.out;
    PrintStream err = System.err;
    if (out != nullSentinel) {
      originalOut = out;
    }
    if (err != nullSentinel) {
      originalErr = err;
    }
  }

  private final List<CharSequence> logLines = Collections.synchronizedList(new ArrayList<>());
  private final AtomicBoolean buildFailed = new AtomicBoolean(false);

  StandardOutputListener listener() {
    return logLines::add;
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
    emit();
    restoreStreamsLate();
  }

  private void restoreStreamsLate() {
    PrintStream out = originalOut;
    PrintStream err = originalErr;
    if (out == null || err == null) return;

    // Restore streams after a short delay to ensure Gradle's final failure reporting
    // (which happens after service close) is caught by the current null redirection.
    ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "llm-compactor-stream-restorer");
              t.setDaemon(true);
              return t;
            });

    pendingRestore =
        scheduler.schedule(
            () -> {
              System.out.flush();
              System.err.flush();
              // Only restore if the streams are still our sentinel. If a new build started in this
              // daemon it has taken over the redirect (and cancelled this future via
              // captureOriginals), so we must not clobber it.
              if (System.out == nullSentinel) {
                System.setOut(out);
              }
              if (System.err == nullSentinel) {
                System.setErr(err);
              }
              pendingRestore = null;
            },
            2000,
            TimeUnit.MILLISECONDS);

    scheduler.shutdown(); // allows the scheduled task to finish, then terminates
  }

  private void emit() {
    Params params = getParameters();
    if (!params.getEnabled().getOrElse(true)) {
      return;
    }

    CompactorConfig config = toConfig(params).resolved();

    List<List<String>> scanResults = new ArrayList<>();
    for (File sourceDir : params.getAllSourceDirs().get()) {
      if (sourceDir.exists()) {
        scanResults.add(
            PackageDiscoverer.discoverPackages(Collections.singletonList(sourceDir.toPath())));
      }
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
    for (File buildDir : params.getAllBuildDirs().get()) {
      try {
        Path testResultsDir = buildDir.toPath().resolve("test-results");
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

    // Filter out generic task failures if we have higher-signal root causes
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
            .withSessionStartTime(params.getSessionStartTime().get())
            .withConfig(config)
            .build();

    // Write default summary file
    Path rootBuildDir = params.getBuildDir().get().toPath();
    SummaryWriter.write(summary, rootBuildDir.resolve("llm-summary.json"));

    // Write to custom path if specified
    if (config.outputPath() != null) {
      SummaryWriter.write(summary, params.getRootDir().get().toPath().resolve(config.outputPath()));
    }

    String renderedSummary;
    if (config.outputAsJson()) {
      renderedSummary = SummaryWriter.toJson(summary, config.testDurationThresholdMs());
    } else {
      renderedSummary =
          SummaryWriter.toHumanReadable(
              summary, config.showSlowTests(), config.testDurationThresholdMs());
    }

    // Using Gradle's internal logger is the most reliable way to ensure the summary
    // is printed to the console even if we've redirected System.out to null.
    org.gradle.api.logging.Logging.getLogger(CompletionService.class).quiet(renderedSummary);

    if (buildFailed.get() && compilationErrors.isEmpty()) {
      org.gradle.api.logging.Logging.getLogger(CompletionService.class)
          .debug(
              "[LLM Compactor] Build failed but no compilation errors extracted. Log lines: "
                  + logLines.size());
    }
  }

  private static CompactorConfig toConfig(Params params) {
    return DefaultCompactorConfig.builder()
        .enabled(params.getEnabled().getOrElse(true))
        .outputPath(params.getOutputPath().getOrNull())
        .mode(params.getMode().getOrNull())
        .outputAsJson(params.getOutputAsJson().getOrElse(true))
        .compressStackFrames(params.getCompressStackFrames().getOrElse(true))
        .showFixTargets(params.getShowFixTargets().getOrElse(false))
        .showRecentChanges(params.getShowRecentChanges().getOrElse(false))
        .showSlowTests(params.getShowSlowTests().getOrElse(true))
        .showTotalDuration(params.getShowTotalDuration().getOrElse(false))
        .showDurationReport(params.getShowDurationReport().getOrElse(false))
        .showFailedTestLogs(params.getShowFailedTestLogs().getOrElse(true))
        .testDurationThresholdMs(params.getTestDurationThresholdMs().getOrElse(100.0))
        .stackFrameWhitelist(params.getStackFrameWhitelist().getOrElse(Collections.emptyList()))
        .stackFrameBlacklist(params.getStackFrameBlacklist().getOrElse(Collections.emptyList()))
        .build();
  }
}
