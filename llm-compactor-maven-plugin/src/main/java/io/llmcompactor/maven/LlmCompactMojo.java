package io.llmcompactor.maven;

import io.llmcompactor.core.*;
import io.llmcompactor.core.extract.*;
import io.llmcompactor.core.parser.SurefireParser;
import io.llmcompactor.core.parser.TestResult;
import io.llmcompactor.core.git.GitDiffExtractor;
import io.llmcompactor.extension.BuildOutputSpy;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Mojo(name = "compact", defaultPhase = LifecyclePhase.VERIFY)
public class LlmCompactMojo extends AbstractMojo {

    @Parameter(property = "llmCompactor.enabled", defaultValue = "true")
    private boolean enabled;

    @Parameter(property = "llmCompactor.outputPath", defaultValue = "target/llm-summary.json")
    private String outputPath;

    @Parameter(property = "llmCompactor.outputAsJson", defaultValue = "true")
    private boolean outputAsJson;

    @Parameter(property = "llmCompactor.compressStackFrames", defaultValue = "true")
    private boolean compressStackFrames;

    @Parameter(property = "llmCompactor.showFixTargets", defaultValue = "true")
    private boolean showFixTargets;

    @Parameter(property = "llmCompactor.showRecentChanges", defaultValue = "true")
    private boolean showRecentChanges;

    @Parameter(property = "llmCompactor.includePackages")
    private String includePackages;

    public void execute() throws MojoExecutionException {

        if (!enabled) {
            return;
        }

        // If BuildOutputSpy is active, it handles everything automatically via SessionEnded event.
        // This Mojo can be skipped when the extension is present to avoid double summaries.
        if (BuildOutputSpy.getRealOut() != null) {
            return;
        }

        List<String> includePackagesList = includePackages == null || includePackages.isEmpty() ?
                List.of() : List.of(includePackages.split(","));

        // Parse test results from existing reports
        TestResult testResult = SurefireParser.parse(Path.of("target"), compressStackFrames, includePackagesList);
        List<BuildError> testFailures = testResult.errors();

        // Get compilation errors (currently empty, can be populated from EventSpy)
        List<BuildError> compileErrors = new ArrayList<>();

        List<BuildError> allErrors = new ArrayList<>();
        allErrors.addAll(compileErrors);
        allErrors.addAll(testFailures);

        List<FixTarget> targets = showFixTargets ?
                FixTargetGenerator.generate(allErrors) : List.of();

        List<String> recentChanges = showRecentChanges ?
                GitDiffExtractor.changedFiles() : List.of();

        BuildSummary summary = new BuildSummary(
                allErrors.isEmpty() ? "SUCCESS" : "FAILED",
                testResult.testsRun(),
                testResult.failures(),
                allErrors,
                targets,
                recentChanges
        );

        SummaryWriter.write(summary, Path.of(outputPath));

        // Output to real System.out captured by BuildOutputSpy
        PrintStream out = BuildOutputSpy.getRealOut();
        if (out == null) {
            out = System.out;
        }

        if (outputAsJson) {
            out.print(SummaryWriter.toJson(summary));
        } else {
            out.println(SummaryWriter.toHumanReadable(summary));
        }
    }
}
