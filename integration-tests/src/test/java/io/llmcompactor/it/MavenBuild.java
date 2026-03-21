package io.llmcompactor.it;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for executing Maven builds in test projects.
 */
public class MavenBuild {
    private final Path projectDir;
    private final List<String> goals = new ArrayList<>();
    private final Map<String, String> properties = new HashMap<>();
    private int timeoutMinutes = 5;

    private MavenBuild(Path projectDir) {
        this.projectDir = projectDir;
    }

    /**
     * Creates a new MavenBuild for a test project in src/test/resources/test-projects.
     */
    public static MavenBuild inProject(String projectName) {
        try {
            URL resource = MavenBuild.class.getClassLoader()
                .getResource("test-projects/" + projectName);
            if (resource == null) {
                throw new RuntimeException("Test project not found: " + projectName);
            }
            Path projectDir = Paths.get(resource.toURI());
            // Ensure mvnw is executable
            Path mvnw = projectDir.resolve("mvnw");
            mvnw.toFile().setExecutable(true);
            return new MavenBuild(projectDir);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to locate test project: " + projectName, e);
        }
    }

    /**
     * Creates a new MavenBuild for an external project directory.
     */
    public static MavenBuild inDirectory(Path projectDir) {
        return new MavenBuild(projectDir);
    }

    /**
     * Adds a Maven goal to execute.
     */
    public MavenBuild withGoal(String goal) {
        goals.add(goal);
        return this;
    }

    /**
     * Adds a Maven property (-Dkey=value).
     */
    public MavenBuild withProperty(String key, String value) {
        properties.put(key, value);
        return this;
    }

    /**
     * Adds a Maven property with boolean true value.
     */
    public MavenBuild withProperty(String key) {
        properties.put(key, "true");
        return this;
    }

    /**
     * Sets the build timeout in minutes.
     */
    public MavenBuild withTimeout(int minutes) {
        this.timeoutMinutes = minutes;
        return this;
    }

    /**
     * Executes the Maven build and returns the result.
     */
    public BuildResult execute() throws IOException, InterruptedException {
        // Use mvnw from the test project directory
        String mvnw = projectDir.resolve("mvnw").toString();
        
        List<String> cmd = new ArrayList<>();
        cmd.add(mvnw);
        cmd.add("-B"); // Batch mode
        cmd.add("-q"); // Quiet mode

        // Add properties
        for (Map.Entry<String, String> prop : properties.entrySet()) {
            cmd.add("-D" + prop.getKey() + "=" + prop.getValue());
        }

        // Add goals
        cmd.addAll(goals);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(projectDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean completed = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("Build timed out after " + timeoutMinutes + " minutes");
        }

        int exitCode = process.exitValue();
        String outputStr = output.toString();

        // Extract JSON from console output (look for { to end of JSON)
        String summaryJson = extractJsonFromOutput(outputStr);

        return new BuildResult(outputStr, summaryJson, exitCode, projectDir.resolve("target"));
    }

    /**
     * Extracts JSON from build output by finding the first { and last }.
     */
    private static String extractJsonFromOutput(String output) {
        int start = output.indexOf('{');
        int end = output.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return output.substring(start, end + 1);
        }
        return null;
    }

    /**
     * Returns the project directory.
     */
    public Path getProjectDir() {
        return projectDir;
    }
}
