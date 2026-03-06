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
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.tasks.testing.Test;
import org.gradle.build.event.BuildEventsListenerRegistry;

import javax.inject.Inject;
import java.io.File;
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

    @Override
    public void apply(Project project) {
        LlmCompactorExtension extension = project.getExtensions().create("llmCompactor", LlmCompactorExtension.class);
        extension.getEnabled().convention(true);
        extension.getOutputAsJson().convention(true);
        extension.getCompressStackFrames().convention(true);
        extension.getShowFixTargets().convention(true);
        extension.getShowRecentChanges().convention(true);

        if (project.equals(project.getRootProject())) {
            // Only emit summary from root project at the end
            project.getGradle().buildFinished(result -> {
                if (Boolean.TRUE.equals(extension.getEnabled().get())) {
                    emitSummary(project, extension);
                }
            });
        }
    }

    private void emitSummary(Project project, LlmCompactorExtension extension) {
        List<BuildError> allErrors = new ArrayList<>();
        int totalTestsRun = 0;
        int totalTestFailures = 0;

        // Collect results from all projects
        for (Project p : project.getAllprojects()) {
            Path testResultsDir = p.getBuildDir().toPath().resolve("test-results");
            TestResult result = GradleParser.parse(
                    testResultsDir, 
                    extension.getCompressStackFrames().get(), 
                    extension.getIncludePackages().get()
            );
            totalTestsRun += result.testsRun();
            totalTestFailures += result.failures();
            allErrors.addAll(result.errors());
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
