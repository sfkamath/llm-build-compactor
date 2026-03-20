package io.llmcompactor.gradle;

import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.BuildSummary;
import io.llmcompactor.core.FixTarget;
import io.llmcompactor.core.SummaryWriter;
import io.llmcompactor.core.extract.CompilationErrorExtractor;
import io.llmcompactor.core.extract.FixTargetGenerator;
import io.llmcompactor.core.git.GitDiffExtractor;
import io.llmcompactor.core.parser.GradleParser;
import io.llmcompactor.core.parser.TestResult;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Gradle plugin that compacts build output for LLM-assisted development.
 * Captures test results and compilation errors, then emits a condensed summary.
 */
public class LlmCompactorPlugin implements Plugin<Project> {
    private static final String ROOT_LISTENER_REGISTERED = "llmCompactorRootListenerRegistered";
    private static final String INIT_SCRIPT_NAME = "llm-compactor-silence.gradle";

    /**
     * Creates a new instance of the LLM Build Compactor plugin.
     */
    public LlmCompactorPlugin() {
    }

    /**
     * Extension configuration for the LLM Build Compactor plugin.
     */
    public interface LlmCompactorExtension {
        /**
         * Whether the plugin is enabled.
         * @return property for enabling/disabling the plugin (default: true)
         */
        Property<Boolean> getEnabled();

        /**
         * Whether to output the summary as JSON.
         * @return property for JSON output (default: false for human-readable)
         */
        Property<Boolean> getOutputAsJson();

        /**
         * Whether to compress stack traces in the output.
         * @return property for stack frame compression (default: true)
         */
        Property<Boolean> getCompressStackFrames();

        /**
         * List of packages to include in the analysis.
         * @return list property of package names to include
         */
        ListProperty<String> getIncludePackages();

        /**
         * Whether to show fix targets for errors.
         * @return property for showing fix targets (default: false)
         */
        Property<Boolean> getShowFixTargets();

        /**
         * Whether to show recent git changes.
         * @return property for showing recent changes (default: false)
         */
        Property<Boolean> getShowRecentChanges();

        /**
         * Whether to show test duration for each error.
         * @return property for showing test duration (default: true)
         */
        Property<Boolean> getShowDuration();

        /**
         * Whether to show total build duration.
         * @return property for showing total duration (default: false)
         */
        Property<Boolean> getShowTotalDuration();

        /**
         * Whether to show test duration percentiles report.
         * @return property for showing duration report (default: false)
         */
        Property<Boolean> getShowDurationReport();

        /**
         * Custom output path for the summary file.
         * @return property for custom output path (default: null for default location)
         */
        Property<String> getOutputPath();
    }

    private final List<CharSequence> logLines = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void apply(Project project) {
        // Idempotent auto-installation of the init script for future builds
        installInitScript(project);

        LlmCompactorExtension extension = project.getExtensions().create("llmCompactor", LlmCompactorExtension.class);
        String sysProp = System.getProperty("llmCompactor.enabled");
        Boolean enabledValue = sysProp != null
            ? Boolean.parseBoolean(sysProp)
            : project.getProviders().gradleProperty("llmCompactor.enabled").isPresent()
                ? Boolean.parseBoolean(project.getProviders().gradleProperty("llmCompactor.enabled").get())
                : true;
        project.getLogger().debug("[LLM Compactor] sysProp={} enabledValue={}", sysProp, enabledValue);
        extension.getEnabled().set(enabledValue);
        extension.getOutputAsJson().convention(false);  // Match Maven default (human-readable)
        extension.getCompressStackFrames().convention(true);
        extension.getShowFixTargets().convention(false);  // Match Maven test-project config
        extension.getShowRecentChanges().convention(false);  // Match Maven test-project config
        extension.getShowDuration().convention(true);
        extension.getShowTotalDuration().convention(false);
        extension.getShowDurationReport().convention(false);
        extension.getOutputPath().convention((String) null);

        // Register the installation task mirroring the Maven install mojo
        project.getTasks().register("installLlmCompactor", task -> {
            task.setGroup("llm-compactor");
            task.setDescription("Installs the LLM Compactor init script");
            task.doLast(t -> {
                try {
                    Path gradleUserHome = project.getGradle().getGradleUserHomeDir().toPath();
                    installInitScript(gradleUserHome);
                    System.out.println("Installed llm-compactor init script to " + gradleUserHome.resolve("init.d"));
                } catch (IOException e) {
                    throw new RuntimeException("Failed to install Gradle init script", e);
                }
            });
        });

        Project rootProject = project.getRootProject();
        if (!rootProject.getExtensions().getExtraProperties().has(ROOT_LISTENER_REGISTERED)) {
            rootProject.getExtensions().getExtraProperties().set(ROOT_LISTENER_REGISTERED, true);
            long sessionStartTime = System.currentTimeMillis();
            final boolean isEnabled = Boolean.TRUE.equals(extension.getEnabled().get());
            final PrintStream originalOut = System.out;
            final PrintStream originalErr = System.err;

            if (isEnabled) {
                System.setProperty("org.gradle.logging.level", "quiet");
                rootProject.getGradle().getStartParameter().setLogLevel(org.gradle.api.logging.LogLevel.QUIET);
                rootProject.getGradle().getStartParameter().setWarningMode(org.gradle.api.logging.configuration.WarningMode.None);
                rootProject.getGradle().getStartParameter().setShowStacktrace(org.gradle.api.logging.configuration.ShowStacktrace.INTERNAL_EXCEPTIONS);
                // Java 8 compatible null output stream
                java.io.OutputStream nullOut = new java.io.OutputStream() {
                    @Override
                    public void write(int b) throws java.io.IOException {
                        // Discard all bytes
                    }
                };
                System.setOut(new PrintStream(nullOut));
                System.setErr(new PrintStream(nullOut));

                rootProject.allprojects(p -> {
                    p.getLogging().captureStandardOutput(org.gradle.api.logging.LogLevel.DEBUG);
                    p.getLogging().captureStandardError(org.gradle.api.logging.LogLevel.DEBUG);
                });
            }

            // Capture output for potential use (init script may have already redirected)
            StandardOutputListener listener = logLines::add;
            rootProject.allprojects(p -> {
                p.getLogging().addStandardOutputListener(listener);
                p.getLogging().addStandardErrorListener(listener);
                p.getTasks().configureEach(task -> {
                    if (isEnabled) {
                        applyQuietTaskLogging(task);
                        task.doFirst(ignored -> applyQuietTaskLogging(task));
                    }
                });
                p.getTasks().withType(JavaCompile.class).configureEach(task -> {
                    if (isEnabled) {
                        applyQuietJavaCompileOptions(task);
                        task.doFirst(ignored -> applyQuietJavaCompileOptions(task));
                    }
                    task.getLogging().addStandardOutputListener(listener);
                    task.getLogging().addStandardErrorListener(listener);
                });
                p.getTasks().withType(Checkstyle.class).configureEach(task -> {
                    if (isEnabled) {
                        task.setShowViolations(false);
                    }
                });
                p.getTasks().withType(Test.class).configureEach(task -> {
                    if (isEnabled) {
                        task.systemProperty("slf4j.internal.verbosity", "ERROR");
                    }
                });
                p.getTasks().withType(JavaExec.class).configureEach(task -> {
                    if (isEnabled) {
                        task.systemProperty("slf4j.internal.verbosity", "ERROR");
                    }
                });
            });

            registerBuildFinishedListener(rootProject, extension, sessionStartTime, isEnabled, originalOut, originalErr);
        }
    }

    @SuppressWarnings("deprecation")
    private void registerBuildFinishedListener(Project rootProject, LlmCompactorExtension extension, long sessionStartTime, boolean isEnabled, PrintStream originalOut, PrintStream originalErr) {
        rootProject.getGradle().buildFinished(result -> {
            if (isEnabled) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.setOut(originalOut);
                System.setErr(originalErr);
                emitSummary(rootProject, extension, sessionStartTime);
            }
        });
    }

    private void installInitScript(Project project) {
        try {
            Path gradleUserHome = project.getGradle().getGradleUserHomeDir().toPath();
            installInitScript(gradleUserHome);
        } catch (Exception e) {
            // Non-fatal
        }
    }

    private void installInitScript(Path gradleUserHome) throws IOException {
        Path initDir = gradleUserHome.resolve("init.d");
        java.nio.file.Files.createDirectories(initDir);
        Path initScript = initDir.resolve(INIT_SCRIPT_NAME);

        try (java.io.InputStream is = getClass().getResourceAsStream("/llm-compactor-init.gradle")) {
            if (is == null) {
                return;
            }
            // Java 8 compatible readAllBytes
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[4096];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            byte[] contentBytes = buffer.toByteArray();
            boolean needsWrite = true;
            if (java.nio.file.Files.exists(initScript)) {
                byte[] existingBytes = java.nio.file.Files.readAllBytes(initScript);
                if (java.util.Arrays.equals(existingBytes, contentBytes)) {
                    needsWrite = false;
                }
            }
            if (needsWrite) {
                java.nio.file.Files.write(initScript, contentBytes);
            }
        }
    }

    private void emitSummary(Project project, LlmCompactorExtension extension, long sessionStartTime) {
        List<BuildError> allErrors = new ArrayList<>();
        List<Double> allDurations = new ArrayList<>();
        int totalTestsRun = 0;
        int totalTestFailures = 0;

        List<String> includePackages = new ArrayList<>(extension.getIncludePackages().getOrElse(Collections.emptyList()));
        for (Project p : project.getAllprojects()) {
            includePackages.addAll(scanProjectPackages(p));
        }

        List<String> stringLogLines = logLines.stream()
                .map(Object::toString)
                .collect(Collectors.toList());

        List<BuildError> compilationErrors = CompilationErrorExtractor.extract(stringLogLines);
        allErrors.addAll(compilationErrors);

        for (Project p : project.getAllprojects()) {
            try {
                Path testResultsDir = p.getLayout().getBuildDirectory().getAsFile().get().toPath().resolve("test-results");
                if (testResultsDir.toFile().exists()) {
                    TestResult result = GradleParser.parse(
                            testResultsDir,
                            extension.getCompressStackFrames().get(),
                            includePackages,
                            sessionStartTime
                    );
                    totalTestsRun += result.testsRun();
                    totalTestFailures += result.failures();
                    allErrors.addAll(result.errors());
                    allDurations.addAll(result.allDurations());
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        Long totalBuildDurationMs = null;
        if (Boolean.TRUE.equals(extension.getShowTotalDuration().get())) {
            totalBuildDurationMs = System.currentTimeMillis() - sessionStartTime;
        }

        Map<String, Double> testDurationPercentiles = null;
        if (Boolean.TRUE.equals(extension.getShowDurationReport().get()) && !allDurations.isEmpty()) {
            Collections.sort(allDurations);
            testDurationPercentiles = new TreeMap<>();
            testDurationPercentiles.put("p50", allDurations.get((int) (allDurations.size() * 0.50)));
            testDurationPercentiles.put("p90", allDurations.get((int) (allDurations.size() * 0.90)));
            testDurationPercentiles.put("p95", allDurations.get((int) (allDurations.size() * 0.95)));
            testDurationPercentiles.put("p99", allDurations.get((int) (allDurations.size() * 0.99)));
            testDurationPercentiles.put("max", allDurations.get(allDurations.size() - 1));
        }

        allErrors = BuildSummary.aggregateErrors(allErrors);

        List<FixTarget> targets = extension.getShowFixTargets().get() ?
                FixTargetGenerator.generate(allErrors) : Collections.emptyList();

        List<String> recentChanges = extension.getShowRecentChanges().get() ?
                GitDiffExtractor.changedFiles() : Collections.emptyList();

        BuildSummary summary = new BuildSummary(
                allErrors.isEmpty() ? "SUCCESS" : "FAILED",
                totalTestsRun,
                totalTestFailures,
                allErrors,
                targets,
                recentChanges,
                totalBuildDurationMs,
                testDurationPercentiles
        );

        if (extension.getOutputPath().isPresent()) {
            SummaryWriter.write(summary, java.nio.file.Paths.get(extension.getOutputPath().get()));
        }

        String renderedSummary;
        if (extension.getOutputAsJson().get()) {
            renderedSummary = SummaryWriter.toJson(summary);
        } else {
            renderedSummary = SummaryWriter.toHumanReadable(summary, extension.getShowDuration().get());
        }
        project.getLogger().quiet(renderedSummary);
    }

    private void applyQuietTaskLogging(Task task) {
        task.getLogging().captureStandardOutput(LogLevel.DEBUG);
        task.getLogging().captureStandardError(LogLevel.DEBUG);
    }

    private void applyQuietJavaCompileOptions(JavaCompile task) {
        task.getOptions().setFork(false);
        task.getOptions().setWarnings(false);
        task.getOptions().setDeprecation(false);
        List<String> compilerArgs = task.getOptions().getCompilerArgs();
        compilerArgs.removeIf(arg -> "-Amicronaut.processing.incremental=true".equals(arg));
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
        if (!containsCompilerArg(compilerArgs, "-Xlint:-removal")) {
            compilerArgs.add("-Xlint:-removal");
        }
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
        List<String> packages = new ArrayList<>();
        org.gradle.api.plugins.JavaPluginExtension javaExtension =
                project.getExtensions().findByType(org.gradle.api.plugins.JavaPluginExtension.class);

        if (javaExtension != null) {
            javaExtension.getSourceSets().all(sourceSet -> {
                for (java.io.File root : sourceSet.getAllJava().getSrcDirs()) {
                    if (root.exists()) {
                        Path rootPath = root.toPath();
                        try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(rootPath)) {
                            walk.filter(java.nio.file.Files::isRegularFile)
                                    .filter(p -> p.toString().endsWith(".java"))
                                    .forEach(p -> {
                                        Path relative = rootPath.relativize(p);
                                        if (relative.getParent() != null) {
                                            String pkg = relative.getParent().toString().replace(java.io.File.separator, ".");
                                            if (!packages.contains(pkg)) {
                                                packages.add(pkg);
                                            }
                                        }
                                    });
                        } catch (java.io.IOException e) {
                            // Ignore
                        }
                    }
                }
            });
        }
        return packages;
    }
}
