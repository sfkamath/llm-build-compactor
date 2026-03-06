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
import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.compile.JavaCompile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LlmCompactorPlugin implements Plugin<Project> {

    public interface LlmCompactorExtension {
        Property<Boolean> getEnabled();
        Property<Boolean> getOutputAsJson();
        Property<Boolean> getCompressStackFrames();
        ListProperty<String> getIncludePackages();
        Property<Boolean> getShowFixTargets();
        Property<Boolean> getShowRecentChanges();
    }

    private final List<CharSequence> logLines = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void apply(Project project) {
        LlmCompactorExtension extension = project.getExtensions().create("llmCompactor", LlmCompactorExtension.class);
        extension.getEnabled().convention(true);
        extension.getOutputAsJson().convention(true);
        extension.getCompressStackFrames().convention(true);
        extension.getShowFixTargets().convention(true);
        extension.getShowRecentChanges().convention(true);

        if (project.equals(project.getRootProject())) {
            StandardOutputListener listener = logLines::add;
            
            project.allprojects(p -> {
                p.getLogging().addStandardOutputListener(listener);
                p.getLogging().addStandardErrorListener(listener);
                
                // Explicitly add to JavaCompile tasks as well
                p.getTasks().withType(JavaCompile.class).configureEach(task -> {
                    task.getLogging().addStandardOutputListener(listener);
                    task.getLogging().addStandardErrorListener(listener);
                });
            });

            project.getGradle().addBuildListener(new BuildAdapter() {
                @Override
                public void buildFinished(BuildResult result) {
                    if (Boolean.TRUE.equals(extension.getEnabled().get())) {
                        emitSummary(project, extension);
                    }
                }
            });
        }
    }

    private void emitSummary(Project project, LlmCompactorExtension extension) {
        List<BuildError> allErrors = new ArrayList<>();
        int totalTestsRun = 0;
        int totalTestFailures = 0;

        // Add compilation errors first
        List<String> stringLogLines = logLines.stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        
        List<BuildError> compilationErrors = CompilationErrorExtractor.extract(stringLogLines);
        allErrors.addAll(compilationErrors);

        // Collect results from all projects
        for (Project p : project.getAllprojects()) {
            try {
                Path testResultsDir = p.getLayout().getBuildDirectory().getAsFile().get().toPath().resolve("test-results");
                if (testResultsDir.toFile().exists()) {
                    TestResult result = GradleParser.parse(
                            testResultsDir,
                            extension.getCompressStackFrames().get(),
                            extension.getIncludePackages().getOrElse(Collections.emptyList())
                    );
                    totalTestsRun += result.testsRun();
                    totalTestFailures += result.failures();
                    allErrors.addAll(result.errors());
                }
            } catch (Exception e) {
                // Ignore
            }
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
                recentChanges
        );

        if (extension.getOutputAsJson().get()) {
            System.out.println(SummaryWriter.toJson(summary));
        } else {
            System.out.println(SummaryWriter.toHumanReadable(summary));
        }
    }
}
