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
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.compile.JavaCompile;

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

public class LlmCompactorPlugin implements Plugin<Project> {

    public interface LlmCompactorExtension {
        Property<Boolean> getEnabled();
        Property<Boolean> getOutputAsJson();
        Property<Boolean> getCompressStackFrames();
        ListProperty<String> getIncludePackages();
        Property<Boolean> getShowFixTargets();
        Property<Boolean> getShowRecentChanges();
        Property<Boolean> getShowDuration();
        Property<Boolean> getShowTotalDuration();
        Property<Boolean> getShowDurationReport();
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
            task.setDescription("Installs the LLM Compactor init script for complete Gradle silence");
            Path initDir = project.getGradle().getGradleUserHomeDir().toPath().resolve("init.d");
            task.doLast(t -> {
                try {
                    java.nio.file.Files.createDirectories(initDir);
                    try (var is = getClass().getResourceAsStream("/llm-compactor-init.gradle")) {
                        if (is != null) {
                            java.nio.file.Files.write(
                                    initDir.resolve("llm-compactor.gradle"),
                                    is.readAllBytes()
                            );
                            System.out.println("Installed llm-compactor init script to " + initDir);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to install init script", e);
                }
            });
        });

        if (project.equals(project.getRootProject())) {
            long sessionStartTime = System.currentTimeMillis();
            final boolean isEnabled = Boolean.TRUE.equals(extension.getEnabled().get());

            // Capture output for potential use (init script may have already redirected)
            StandardOutputListener listener = logLines::add;
            project.allprojects(p -> {
                p.getLogging().addStandardOutputListener(listener);
                p.getLogging().addStandardErrorListener(listener);
                p.getTasks().withType(JavaCompile.class).configureEach(task -> {
                    task.getLogging().addStandardOutputListener(listener);
                    task.getLogging().addStandardErrorListener(listener);
                });
            });

            project.getGradle().buildFinished(result -> {
                if (isEnabled) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    emitSummary(project, extension, sessionStartTime);
                }
            });
        }
    }

    private void installInitScript(Project project) {
        try {
            Path initDir = project.getGradle().getGradleUserHomeDir().toPath().resolve("init.d");
            java.nio.file.Files.createDirectories(initDir);
            Path initScript = initDir.resolve("llm-compactor-silence.gradle");

            try (var is = getClass().getResourceAsStream("/llm-compactor-init.gradle")) {
                if (is != null) {
                    byte[] contentBytes = is.readAllBytes();
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
        } catch (Exception e) {
            // Non-fatal
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
            SummaryWriter.write(summary, Path.of(extension.getOutputPath().get()));
        }

        // Output summary to System.err
        if (extension.getOutputAsJson().get()) {
            System.err.println(SummaryWriter.toJson(summary));
        } else {
            System.err.println(SummaryWriter.toHumanReadable(summary, extension.getShowDuration().get()));
        }
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
