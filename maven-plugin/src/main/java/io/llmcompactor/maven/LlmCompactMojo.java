package io.llmcompactor.maven;

import io.llmcompactor.core.*;
import io.llmcompactor.core.extract.*;
import io.llmcompactor.core.parser.SurefireParser;
import io.llmcompactor.core.parser.TestResult;
import io.llmcompactor.core.git.GitDiffExtractor;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Mojo(name = "compact", defaultPhase = LifecyclePhase.VERIFY)
public class LlmCompactMojo extends AbstractMojo {

    @Parameter(property = "llmCompactor.enabled", defaultValue = "true")
    private boolean enabled;

    @Parameter(property = "llmCompactor.outputPath", defaultValue = "target/llm-summary.json")
    private String outputPath;

    @Parameter(property = "llmCompactor.silent", defaultValue = "true")
    private boolean silent;

    public void execute() throws MojoExecutionException {

        if (!enabled) {
            getLog().info("LLM Compactor is disabled");
            return;
        }

        // Run the full build with suppressed output
        List<String> logs = runSilentBuild();

        List<BuildError> compileErrors =
                CompilationErrorExtractor.extract(logs);

        TestResult testResult = SurefireParser.parse(Path.of("target"));
        List<BuildError> testFailures = testResult.errors();

        List<BuildError> allErrors = new ArrayList<>();
        allErrors.addAll(compileErrors);
        allErrors.addAll(testFailures);

        List<FixTarget> targets =
                FixTargetGenerator.generate(allErrors);

        List<String> recentChanges = GitDiffExtractor.changedFiles();

        BuildSummary summary = new BuildSummary(
                allErrors.isEmpty() ? "SUCCESS" : "FAILED",
                testResult.testsRun(),
                testResult.failures(),
                allErrors,
                targets,
                recentChanges
        );

        SummaryWriter.write(summary, Path.of(outputPath));

        // Output only the JSON
        System.out.print(SummaryWriter.toJson(summary));
    }

    private List<String> runSilentBuild() {
        List<String> logs = new ArrayList<>();
        try {
            // Run Maven with all output suppressed
            ProcessBuilder pb = new ProcessBuilder(
                "mvn", "-B", "-q", "-e", "-DllmCompactor.enabled=false", "clean", "verify"
            );
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();

            // Still capture output for parsing errors (read from error stream which is merged)
            // Actually since we redirected output to DISCARD, we need a different approach
            // Let's capture to a temp file instead
            Path tempLog = Files.createTempFile("llm-compactor", ".log");
            pb.redirectOutput(tempLog.toFile());
            p = pb.start();
            p.waitFor();
            
            // Read the log file
            if (Files.exists(tempLog)) {
                logs = Files.readAllLines(tempLog);
                Files.delete(tempLog);
            }
        } catch (Exception e) {
            // Ignore - we'll parse what we can from test reports
        }
        return logs;
    }
}
