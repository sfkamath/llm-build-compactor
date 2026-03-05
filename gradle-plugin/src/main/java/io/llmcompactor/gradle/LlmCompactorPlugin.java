package io.llmcompactor.gradle;

import io.llmcompactor.core.*;
import io.llmcompactor.core.extract.CompilationErrorExtractor;
import io.llmcompactor.core.extract.FixTargetGenerator;
import io.llmcompactor.core.git.GitDiffExtractor;
import io.llmcompactor.core.parser.SurefireParser;
import io.llmcompactor.core.parser.TestResult;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.testing.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LlmCompactorPlugin implements Plugin<Project> {

    public void apply(Project project) {

        project.getTasks().withType(Test.class).configureEach(test -> {

            test.doLast(task -> {
                generateSummary(project);
            });

        });

    }

    private void generateSummary(Project project) {

        List<String> logs = new ArrayList<>();

        Path buildLog = Path.of("build.log");
        if (Files.exists(buildLog)) {
            try {
                logs = Files.readAllLines(buildLog);
            } catch (Exception ignored) {}
        }

        List<BuildError> compileErrors = CompilationErrorExtractor.extract(logs);

        Path reportsDir = Path.of(project.getBuildDir(), "test-results");
        TestResult testResult = SurefireParser.parse(reportsDir);
        List<BuildError> testFailures = testResult.errors();

        List<BuildError> allErrors = new ArrayList<>();
        allErrors.addAll(compileErrors);
        allErrors.addAll(testFailures);

        List<FixTarget> targets = FixTargetGenerator.generate(allErrors);
        List<String> recentChanges = GitDiffExtractor.changedFiles();

        BuildSummary summary = new BuildSummary(
                allErrors.isEmpty() ? "SUCCESS" : "FAILED",
                testResult.testsRun(),
                testResult.failures(),
                allErrors,
                targets,
                recentChanges
        );

        Path outputPath = Path.of(project.getBuildDir(), "llm-summary.json");
        SummaryWriter.write(summary, outputPath);

        System.out.println("LLM summary written to " + outputPath);
    }
}
