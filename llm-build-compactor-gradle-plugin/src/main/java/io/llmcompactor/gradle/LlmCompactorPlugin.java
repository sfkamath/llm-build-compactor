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
import io.llmcompactor.core.parser.ParserUtils;
import io.llmcompactor.core.parser.TestResultAggregator;
import io.llmcompactor.core.util.ConfigAccessor;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.logging.TestLogEvent;

/**
 * Gradle plugin that compacts build output for LLM-assisted development. Captures test results and
 * compilation errors, then emits a condensed summary.
 */
public class LlmCompactorPlugin implements Plugin<Project> {
  private static final String ROOT_LISTENER_REGISTERED = "llmCompactorRootListenerRegistered";
  private static final String INIT_SCRIPT_NAME = "llm-compactor-silence.gradle";
  private static final String GRADLE_PROPS_MARKER_START = "# >>> llm-compactor >>>";
  private static final String GRADLE_PROPS_MARKER_END = "# <<< llm-compactor <<<";

  /** Creates a new instance of the LLM Build Compactor plugin. */
  public LlmCompactorPlugin() {}

  /** Extension configuration for the LLM Build Compactor plugin. */
  public interface LlmCompactorExtension {
    /**
     * Whether the plugin is enabled.
     *
     * @return property for enabling/disabling the plugin (default: true)
     */
    Property<Boolean> getEnabled();

    /**
     * Whether to output the summary as JSON.
     *
     * @return property for JSON output (default: true)
     */
    Property<Boolean> getOutputAsJson();

    /**
     * Whether to compress stack traces in the output.
     *
     * @return property for stack frame compression (default: true)
     */
    Property<Boolean> getCompressStackFrames();

    /**
     * List of packages to include in stack traces (whitelist).
     *
     * @return list property of package names to always include in stack traces
     */
    ListProperty<String> getStackFrameWhitelist();

    /**
     * List of packages to exclude from stack traces (blacklist).
     *
     * @return list property of package names to always exclude from stack traces
     */
    ListProperty<String> getStackFrameBlacklist();

    /**
     * Whether to show fix targets for errors.
     *
     * @return property for showing fix targets (default: false)
     */
    Property<Boolean> getShowFixTargets();

    /**
     * Whether to show recent git changes.
     *
     * @return property for showing recent changes (default: false)
     */
    Property<Boolean> getShowRecentChanges();

    /**
     * Output mode preset. When set, overrides individual flags.
     *
     * @return property for mode preset (default: null)
     */
    Property<String> getMode();

    /**
     * Whether to show test duration for slow tests (above threshold).
     *
     * @return property for showing slow test durations (default: true)
     */
    Property<Boolean> getShowSlowTests();

    /**
     * Whether to show total build duration.
     *
     * @return property for showing total duration (default: false)
     */
    Property<Boolean> getShowTotalDuration();

    /**
     * Whether to show test duration percentiles report.
     *
     * @return property for showing duration report (default: false)
     */
    Property<Boolean> getShowDurationReport();

    /**
     * Custom output path for the summary file.
     *
     * @return property for custom output path (default: null for default location)
     */
    Property<String> getOutputPath();

    /**
     * Whether to show logs from failed tests.
     *
     * @return property for showing failed test logs (default: true)
     */
    Property<Boolean> getShowFailedTestLogs();

    /**
     * Threshold in milliseconds for showing test duration. Tests with duration below this threshold
     * won't show duration.
     *
     * @return property for test duration threshold (default: 100)
     */
    Property<Double> getTestDurationThresholdMs();

    /**
     * Handles unknown properties gracefully for backward compatibility. Allows newer plugin
     * versions to work with older build scripts setting properties that don't exist yet.
     *
     * @param name the property name that was set
     * @param value the value being set
     * @return null (property is ignored)
     */
    default Object propertyMissing(String name, Object value) {
      System.err.println(
          "[LLM Compactor] Warning: Unknown property '"
              + name
              + "' - this may be from a newer plugin version");
      return null;
    }
  }

  private final List<CharSequence> logLines = Collections.synchronizedList(new ArrayList<>());
  private final AtomicBoolean buildFailed = new AtomicBoolean(false);

  @Override
  public void apply(Project project) {
    LlmCompactorExtension extension =
        project.getExtensions().create("llmCompactor", LlmCompactorExtension.class);

    // llmce is a kill-switch alias; llmCompactor.enabled=false also disables
    boolean llmcePresent =
        System.getProperty("llmce") != null
            || project.getProviders().gradleProperty("llmce").isPresent();
    String enabledProp =
        System.getProperty("llmCompactor.enabled") != null
            ? System.getProperty("llmCompactor.enabled")
            : project.getProviders().gradleProperty("llmCompactor.enabled").isPresent()
                ? project.getProviders().gradleProperty("llmCompactor.enabled").get()
                : null;
    boolean enabledValue = CompactorDefaults.resolveEnabled(llmcePresent, enabledProp);
    project
        .getLogger()
        .debug("[LLM Compactor] llmcePresent={} enabledValue={}", llmcePresent, enabledValue);
    extension.getEnabled().set(enabledValue);

    // Bind extension properties to gradle properties with defaults
    ProviderFactory providers = project.getProviders();
    extension
        .getOutputAsJson()
        .convention(boolProp(providers, "outputAsJson", CompactorConfig.DEFAULT_OUTPUT_AS_JSON));
    extension
        .getCompressStackFrames()
        .convention(
            boolProp(
                providers, "compressStackFrames", CompactorConfig.DEFAULT_COMPRESS_STACK_FRAMES));
    extension
        .getShowFixTargets()
        .convention(
            boolProp(providers, "showFixTargets", CompactorConfig.DEFAULT_SHOW_FIX_TARGETS));
    extension
        .getShowRecentChanges()
        .convention(
            boolProp(providers, "showRecentChanges", CompactorConfig.DEFAULT_SHOW_RECENT_CHANGES));
    extension
        .getShowSlowTests()
        .convention(boolProp(providers, "showSlowTests", CompactorConfig.DEFAULT_SHOW_SLOW_TESTS));
    extension
        .getShowTotalDuration()
        .convention(
            boolProp(providers, "showTotalDuration", CompactorConfig.DEFAULT_SHOW_TOTAL_DURATION));
    extension
        .getShowDurationReport()
        .convention(
            boolProp(
                providers, "showDurationReport", CompactorConfig.DEFAULT_SHOW_DURATION_REPORT));
    extension
        .getShowFailedTestLogs()
        .convention(
            boolProp(
                providers, "showFailedTestLogs", CompactorConfig.DEFAULT_SHOW_FAILED_TEST_LOGS));
    extension
        .getTestDurationThresholdMs()
        .convention(
            doubleProp(
                providers,
                "testDurationThresholdMs",
                CompactorConfig.DEFAULT_TEST_DURATION_THRESHOLD_MS));
    extension.getStackFrameWhitelist().convention(listProp(providers, "stackFrameWhitelist"));
    extension.getStackFrameBlacklist().convention(listProp(providers, "stackFrameBlacklist"));

    Provider<String> modeProp = stringProp(providers, "mode");
    if (modeProp.isPresent()) {
      extension.getMode().set(modeProp.get());
    }
    Provider<String> outputPathProp = stringProp(providers, "outputPath");
    if (outputPathProp.isPresent()) {
      extension.getOutputPath().set(outputPathProp.get());
    }

    // Register the manual installation task
    project
        .getTasks()
        .register(
            "installLlmCompactor",
            task -> {
              task.setGroup("llm-compactor");
              task.setDescription("Installs the LLM Compactor init script into ~/.gradle/init.d/");
              task.doLast(
                  t -> {
                    try {
                      Path gradleUserHome = project.getGradle().getGradleUserHomeDir().toPath();
                      installInitScript(gradleUserHome);
                      installGradleProperties(gradleUserHome);
                      t.getProject()
                          .getLogger()
                          .quiet(
                              "[LLM Compactor] Installed init script at {} and set"
                                  + " org.gradle.logging.level=quiet in ~/.gradle/gradle.properties."
                                  + " This suppresses output for ALL Gradle builds on this"
                                  + " machine. To remove it: ./gradlew uninstallLlmCompactor"
                                  + " && ./gradlew --stop",
                              gradleUserHome.resolve("init.d").resolve(INIT_SCRIPT_NAME));
                    } catch (IOException e) {
                      throw new RuntimeException("Failed to install Gradle init script", e);
                    }
                  });
            });

    // Register the uninstallation task
    project
        .getTasks()
        .register(
            "uninstallLlmCompactor",
            task -> {
              task.setGroup("llm-compactor");
              task.setDescription("Removes the LLM Compactor init script from ~/.gradle/init.d/");
              task.doLast(
                  t -> {
                    Path gradleUserHome = project.getGradle().getGradleUserHomeDir().toPath();
                    Path initScript = gradleUserHome.resolve("init.d").resolve(INIT_SCRIPT_NAME);
                    try {
                      boolean initScriptRemoved = Files.deleteIfExists(initScript);
                      boolean propsRemoved = uninstallGradleProperties(gradleUserHome);
                      if (initScriptRemoved || propsRemoved) {
                        t.getProject()
                            .getLogger()
                            .quiet(
                                "[LLM Compactor] Removed init script ({}) and gradle.properties"
                                    + " entry ({})."
                                    + " IMPORTANT: run './gradlew --stop' to kill the running"
                                    + " daemon — it loaded the init script at startup and will"
                                    + " continue suppressing output until restarted.",
                                initScriptRemoved ? "yes" : "not found",
                                propsRemoved ? "yes" : "not found");
                      } else {
                        t.getProject()
                            .getLogger()
                            .quiet(
                                "[LLM Compactor] Nothing to remove (init script and"
                                    + " gradle.properties entry not found).");
                      }
                    } catch (IOException e) {
                      throw new RuntimeException("Failed to uninstall LLM Compactor", e);
                    }
                  });
            });

    // Auto-install on first apply (idempotent: no-op if already up to date)
    try {
      Path gradleUserHome = project.getGradle().getGradleUserHomeDir().toPath();
      Path initScript = gradleUserHome.resolve("init.d").resolve(INIT_SCRIPT_NAME);
      boolean isNew = !Files.exists(initScript);
      installInitScript(gradleUserHome);
      installGradleProperties(gradleUserHome);
      if (isNew) {
        project
            .getLogger()
            .warn(
                "[LLM Compactor] Installed init script at {} and set"
                    + " org.gradle.logging.level=quiet in ~/.gradle/gradle.properties."
                    + " This suppresses output for ALL Gradle builds on this machine, not just"
                    + " this project. To remove it: ./gradlew uninstallLlmCompactor"
                    + " && ./gradlew --stop",
                initScript);
      }
    } catch (IOException e) {
      project.getLogger().warn("[LLM Compactor] Could not install: {}", e.getMessage());
    }

    Project rootProject = project.getRootProject();
    if (!rootProject.getExtensions().getExtraProperties().has(ROOT_LISTENER_REGISTERED)) {
      rootProject.getExtensions().getExtraProperties().set(ROOT_LISTENER_REGISTERED, true);
      long sessionStartTime = System.currentTimeMillis();
      final boolean isEnabled = Boolean.TRUE.equals(extension.getEnabled().get());
      final PrintStream originalOut = System.out;
      final PrintStream originalErr = System.err;

      if (isEnabled) {
        System.setProperty("org.gradle.logging.level", "quiet");
        rootProject
            .getGradle()
            .getStartParameter()
            .setLogLevel(org.gradle.api.logging.LogLevel.QUIET);
        rootProject
            .getGradle()
            .getStartParameter()
            .setWarningMode(org.gradle.api.logging.configuration.WarningMode.None);
        rootProject
            .getGradle()
            .getStartParameter()
            .setShowStacktrace(
                org.gradle.api.logging.configuration.ShowStacktrace.INTERNAL_EXCEPTIONS);
        PrintStream nullPrint = CompactorDefaults.nullPrintStream();
        System.setOut(nullPrint);
        System.setErr(nullPrint);

        rootProject.allprojects(
            p -> {
              p.getLogging().captureStandardOutput(org.gradle.api.logging.LogLevel.DEBUG);
              p.getLogging().captureStandardError(org.gradle.api.logging.LogLevel.DEBUG);
            });
      }

      // Capture output for potential use (init script may have already redirected)
      StandardOutputListener listener = logLines::add;
      rootProject.allprojects(
          p -> {
            p.getLogging().addStandardOutputListener(listener);
            p.getLogging().addStandardErrorListener(listener);
            p.getTasks()
                .configureEach(
                    task -> {
                      if (isEnabled) {
                        applyQuietTaskLogging(task);
                        task.doFirst(ignored -> applyQuietTaskLogging(task));
                      }
                    });
            p.getTasks()
                .withType(JavaCompile.class)
                .configureEach(
                    task -> {
                      if (isEnabled) {
                        applyQuietJavaCompileOptions(task);
                        task.doFirst(ignored -> applyQuietJavaCompileOptions(task));
                      }
                      task.getLogging().addStandardOutputListener(listener);
                      task.getLogging().addStandardErrorListener(listener);
                    });
            p.getTasks()
                .withType(Checkstyle.class)
                .configureEach(
                    task -> {
                      if (isEnabled) {
                        task.setShowViolations(false);
                      }
                    });
            p.getTasks()
                .withType(Test.class)
                .configureEach(
                    task -> {
                      if (isEnabled) {
                        task.systemProperty("slf4j.internal.verbosity", "ERROR");
                        task.getTestLogging().setEvents(EnumSet.noneOf(TestLogEvent.class));
                        task.getTestLogging().setShowStandardStreams(false);
                        task.getTestLogging().setShowExceptions(false);
                        task.getTestLogging().setShowCauses(false);
                        task.getTestLogging().setShowStackTraces(false);
                        task.addTestOutputListener(
                            (descriptor, event) -> {
                              // Swallow test output from build log; XML results capture it
                            });
                      }
                    });
            p.getTasks()
                .withType(JavaExec.class)
                .configureEach(
                    task -> {
                      if (isEnabled) {
                        task.systemProperty("slf4j.internal.verbosity", "ERROR");
                      }
                    });
          });

      registerBuildFinishedListener(
          rootProject, extension, sessionStartTime, isEnabled, originalOut, originalErr);
    }
  }

  @SuppressWarnings("deprecation")
  private void registerBuildFinishedListener(
      Project rootProject,
      LlmCompactorExtension extension,
      long sessionStartTime,
      boolean isEnabled,
      PrintStream originalOut,
      PrintStream originalErr) {
    rootProject
        .getGradle()
        .buildFinished(
            result -> {
              if (result.getFailure() != null) {
                buildFailed.set(true);
              }
              if (isEnabled) {
                // Brief wait for Gradle's async logging queue to drain to the null stream
                // before restoring stdout, preventing stray task output from appearing after
                // the summary. TODO: replace with a proper Gradle BuildService flush once
                // the deprecated buildFinished API is migrated.
                try {
                  Thread.sleep(50);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                System.setOut(originalOut);
                System.setErr(originalErr);
                emitSummary(rootProject, extension, sessionStartTime);
              }
            });
  }

  private void installInitScript(Path gradleUserHome) throws IOException {
    Path initDir = gradleUserHome.resolve("init.d");
    Files.createDirectories(initDir);
    Path initScript = initDir.resolve(INIT_SCRIPT_NAME);

    try (InputStream is = getClass().getResourceAsStream("/llm-compactor-init.gradle")) {
      if (is == null) {
        return;
      }
      // Java 8 compatible readAllBytes
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      int nRead;
      byte[] data = new byte[4096];
      while ((nRead = is.read(data, 0, data.length)) != -1) {
        buffer.write(data, 0, nRead);
      }
      byte[] contentBytes = buffer.toByteArray();
      boolean needsWrite = true;
      if (Files.exists(initScript)) {
        byte[] existingBytes = Files.readAllBytes(initScript);
        if (Arrays.equals(existingBytes, contentBytes)) {
          needsWrite = false;
        }
      }
      if (needsWrite) {
        Files.write(initScript, contentBytes);
      }
    }
  }

  private void installGradleProperties(Path gradleUserHome) throws IOException {
    Path propsFile = gradleUserHome.resolve("gradle.properties");
    String content =
        Files.exists(propsFile) ? new String(Files.readAllBytes(propsFile), "UTF-8") : "";
    if (content.contains(GRADLE_PROPS_MARKER_START)) {
      return; // already installed
    }
    String block =
        "\n"
            + GRADLE_PROPS_MARKER_START
            + "\n"
            + "org.gradle.logging.level=quiet\n"
            + GRADLE_PROPS_MARKER_END
            + "\n";
    if (!content.isEmpty() && !content.endsWith("\n")) {
      content += "\n";
    }
    Files.write(propsFile, (content + block).getBytes("UTF-8"));
  }

  private boolean uninstallGradleProperties(Path gradleUserHome) throws IOException {
    Path propsFile = gradleUserHome.resolve("gradle.properties");
    if (!Files.exists(propsFile)) {
      return false;
    }
    String content = new String(Files.readAllBytes(propsFile), "UTF-8");
    int start = content.indexOf(GRADLE_PROPS_MARKER_START);
    if (start < 0) {
      return false;
    }
    int end = content.indexOf(GRADLE_PROPS_MARKER_END, start);
    if (end < 0) {
      return false;
    }
    end += GRADLE_PROPS_MARKER_END.length();
    if (end < content.length() && content.charAt(end) == '\n') {
      end++;
    }
    // absorb a preceding blank line if present
    if (start > 0 && content.charAt(start - 1) == '\n') {
      start--;
    }
    String cleaned = content.substring(0, start) + content.substring(end);
    Files.write(propsFile, cleaned.getBytes("UTF-8"));
    return true;
  }

  private void emitSummary(
      Project project, LlmCompactorExtension extension, long sessionStartTime) {
    CompactorConfig config = toConfig(extension).resolved();

    List<List<String>> scanResults = new ArrayList<>();
    for (Project p : project.getAllprojects()) {
      scanResults.add(scanProjectPackages(p));
    }
    List<String> whitelist =
        DefaultCompactorConfig.mergeWhitelist(config.stackFrameWhitelist(), scanResults);
    List<String> blacklist = config.stackFrameBlacklist();

    String fullOutput =
        logLines.stream()
            .map(line -> CompilationErrorExtractor.stripAnsi(line.toString()))
            .collect(Collectors.joining("\n"));
    List<BuildError> compilationErrors =
        CompilationErrorExtractor.extractOrWrap(fullOutput, "build.gradle");

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
                  sessionStartTime,
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

  private static CompactorConfig toConfig(LlmCompactorExtension ext) {
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

  private void applyQuietTaskLogging(Task task) {
    task.getLogging().captureStandardOutput(LogLevel.DEBUG);
    task.getLogging().captureStandardError(LogLevel.DEBUG);
  }

  private void applyQuietJavaCompileOptions(JavaCompile task) {
    task.getOptions().setFork(false);
    task.getOptions().setWarnings(false);
    task.getOptions().setDeprecation(false);
    // Groovy DSL may populate compilerArgs with GStringImpl; normalize to plain Strings
    // to avoid ClassCastException when iterating a List<String> that contains GString values.
    @SuppressWarnings("unchecked")
    List<Object> rawArgs = (List<Object>) (List<?>) task.getOptions().getCompilerArgs();
    List<String> compilerArgs = new ArrayList<>();
    for (Object arg : rawArgs) {
      compilerArgs.add(arg.toString());
    }
    task.getOptions().setCompilerArgs(compilerArgs);
    compilerArgs.removeIf("-Amicronaut.processing.incremental=true"::equals);
    if (!containsCompilerArg(compilerArgs, "-nowarn")) {
      compilerArgs.add("-nowarn");
    }
    if (!containsCompilerArg(compilerArgs, "-Xlint:none")) {
      compilerArgs.add("-Xlint:none");
    }
    if (!containsCompilerArg(compilerArgs, "-Xlint:-processing")) {
      compilerArgs.add("-Xlint:-processing");
    }
    if (!containsCompilerArg(compilerArgs, "-Xlint:-unchecked")) {
      compilerArgs.add("-Xlint:-unchecked");
    }
    if (!containsCompilerArg(compilerArgs, "-Xlint:-deprecation")) {
      compilerArgs.add("-Xlint:-deprecation");
    }
    // -Xlint:-removal is Java 11+ only, skip for Java 8 compatibility
  }

  private boolean containsCompilerArg(List<String> compilerArgs, String value) {
    for (String arg : compilerArgs) {
      if (value.equals(arg)) {
        return true;
      }
    }
    return false;
  }

  private List<String> scanProjectPackages(Project project) {
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

  private static Provider<Boolean> boolProp(
      ProviderFactory providers, String name, boolean defaultValue) {
    return providers
        .gradleProperty("llmCompactor." + name)
        .orElse(providers.systemProperty("llmCompactor." + name))
        .map(v -> ConfigAccessor.parseBoolean(v))
        .orElse(defaultValue);
  }

  private static Provider<Double> doubleProp(
      ProviderFactory providers, String name, double defaultValue) {
    return providers
        .gradleProperty("llmCompactor." + name)
        .orElse(providers.systemProperty("llmCompactor." + name))
        .map(v -> ConfigAccessor.parseDouble(v, defaultValue))
        .orElse(defaultValue);
  }

  private static Provider<List<String>> listProp(ProviderFactory providers, String name) {
    return providers
        .gradleProperty("llmCompactor." + name)
        .orElse(providers.systemProperty("llmCompactor." + name))
        .map(ParserUtils::splitCsv)
        .orElse(Collections.emptyList());
  }

  private static Provider<String> stringProp(ProviderFactory providers, String name) {
    return providers
        .gradleProperty("llmCompactor." + name)
        .orElse(providers.systemProperty("llmCompactor." + name));
  }
}
