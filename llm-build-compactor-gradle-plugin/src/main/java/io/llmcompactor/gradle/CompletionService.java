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
import java.nio.file.Paths;
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

/** Build service that captures output and emits the compact summary on build completion. */
public abstract class CompletionService
    implements BuildService<CompletionService.Params>, OperationCompletionListener, AutoCloseable {

  /** Parameters for the build service. */
  public interface Params extends BuildServiceParameters {
    /**
     * The session start time in millis since epoch.
     *
     * @return the session start time
     */
    Property<Long> getSessionStartTime();
  }

  /** Constructs the completion service (injected by Gradle). */
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
    if (event instanceof TaskFinishEvent
        && ((TaskFinishEvent) event).getResult() instanceof TaskFailureResult) {
      buildFailed.set(true);
    }
  }

  @Override
  public void close() {
    System.setOut(originalOut);
    System.setErr(originalErr);
    emit(rootProject, extension, getParameters().getSessionStartTime().get());
    System.setOut(CompactorDefaults.nullPrintStream());
    System.setErr(CompactorDefaults.nullPrintStream());
  }

  private void emit(
      Project project, LlmCompactorPlugin.LlmCompactorExtension ext, long sessionStartTime) {
    CompactorConfig config = toConfig(ext).resolved();

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
      compilationErrors = CompilationErrorExtractor.extractOrWrap(fullOutput, "build.gradle");
    } else {
      compilationErrors = Collections.emptyList();
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

    BuildSummary summary =
        new SummaryBuilder()
            .addErrors(compilationErrors)
            .addErrors(testResults.errors())
            .addDurations(testResults.allDurations())
            .addSlowTests(testResults.slowTests())
            .withTestsRun(testResults.testsRun())
            .withFailures(testResults.failures())
            .withBuildFailed(buildFailed.get())
            .withSessionStartTime(sessionStartTime)
            .withConfig(config)
            .build();

    if (config.outputPath() != null) {
      SummaryWriter.write(summary, Paths.get(config.outputPath()));
    }

    String renderedSummary;
    if (config.outputAsJson()) {
      renderedSummary = SummaryWriter.toJson(summary, config.testDurationThresholdMs());
    } else {
      renderedSummary =
          SummaryWriter.toHumanReadable(
              summary, config.showSlowTests(), config.testDurationThresholdMs());
    }
    project.getLogger().quiet(renderedSummary);
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

  private static List<String> scanProjectPackages(Project project) {
    org.gradle.api.plugins.JavaPluginExtension javaExtension =
        project.getExtensions().findByType(org.gradle.api.plugins.JavaPluginExtension.class);
    if (javaExtension == null) {
      return Collections.emptyList();
    }
    List<Path> roots = new ArrayList<>();
    javaExtension
        .getSourceSets()
        .all(
            sourceSet -> {
              for (File root : sourceSet.getAllJava().getSrcDirs()) {
                roots.add(root.toPath());
              }
            });
    return PackageDiscoverer.discoverPackages(roots);
  }
}
