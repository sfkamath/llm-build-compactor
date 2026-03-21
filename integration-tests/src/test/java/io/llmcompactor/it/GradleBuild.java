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
 * Helper class for executing Gradle builds in test projects.
 */
public class GradleBuild {
    private final Path projectDir;
    private final List<String> tasks = new ArrayList<>();
    private final Map<String, String> properties = new HashMap<>();
    private int timeoutMinutes = 5;
    
    // Shared Gradle user home for all tests in a suite run
    // This allows dependency caching while isolating from the user's real Gradle cache
    private static final Path GRADLE_TEST_HOME;
    static {
        try {
            GRADLE_TEST_HOME = Paths.get(System.getProperty("java.io.tmpdir"), "llm-compactor-gradle-test-home");
            cleanupTestHome();
            Files.createDirectories(GRADLE_TEST_HOME);
            
            // Seed wrapper dists from real Gradle home to avoid downloading Gradle distribution
            Path realGradleHome = Paths.get(System.getProperty("user.home"), ".gradle");
            Path wrapperDists = realGradleHome.resolve("wrapper/dists");
            if (Files.exists(wrapperDists)) {
                copyDirectory(wrapperDists, GRADLE_TEST_HOME.resolve("wrapper/dists"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize shared Gradle test home", e);
        }
    }

    private GradleBuild(Path projectDir) {
        this.projectDir = projectDir;
    }

    /**
     * Creates a new GradleBuild for a test project in src/test/resources/test-projects.
     */
    public static GradleBuild inProject(String projectName) {
        try {
            URL resource = GradleBuild.class.getClassLoader()
                .getResource("test-projects/" + projectName);
            if (resource == null) {
                throw new RuntimeException("Test project not found: " + projectName);
            }
            Path projectDir = Paths.get(resource.toURI());
            // Ensure gradlew is executable
            Path gradlew = projectDir.resolve("gradlew");
            gradlew.toFile().setExecutable(true);
            return new GradleBuild(projectDir);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to locate test project: " + projectName, e);
        }
    }

    /**
     * Creates a new GradleBuild for an external project directory.
     */
    public static GradleBuild inDirectory(Path projectDir) {
        return new GradleBuild(projectDir);
    }

    /**
     * Adds a Gradle task to execute.
     */
    public GradleBuild withTask(String task) {
        tasks.add(task);
        return this;
    }

    /**
     * Adds a Gradle project property (-Pkey=value).
     */
    public GradleBuild withProperty(String key, String value) {
        properties.put(key, value);
        return this;
    }

    /**
     * Adds a Gradle project property with boolean true value.
     */
    public GradleBuild withProperty(String key) {
        properties.put(key, "true");
        return this;
    }

    /**
     * Sets the build timeout in minutes.
     */
    public GradleBuild withTimeout(int minutes) {
        this.timeoutMinutes = minutes;
        return this;
    }

    /**
     * Executes the Gradle build and returns the result.
     */
    public BuildResult execute() throws IOException, InterruptedException {
        // Clean build directory to ensure fresh test outputs (but keep .gradle cache for dependencies)
        deleteDirectory(projectDir.resolve("build"));
        
        // Use gradlew from the test project directory
        String gradlew = projectDir.resolve("gradlew").toString();

        // Use shared Gradle user home for dependency caching across tests
        String gradleUserHome = GRADLE_TEST_HOME.toAbsolutePath().toString();

        List<String> cmd = new ArrayList<>();
        cmd.add(gradlew);
        cmd.add("--daemon"); // Use daemon for faster subsequent builds

        // Add project properties
        for (Map.Entry<String, String> prop : properties.entrySet()) {
            cmd.add("-P" + prop.getKey() + "=" + prop.getValue());
        }

        // Add tasks
        cmd.addAll(tasks);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(projectDir.toFile());
        pb.redirectErrorStream(true);
        pb.environment().put("GRADLE_USER_HOME", gradleUserHome);

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

        return new BuildResult(outputStr, summaryJson, exitCode, projectDir.resolve("build"), projectDir);
    }

    /**
     * Recursively deletes a directory.
     */
    private static void deleteDirectory(Path path) throws IOException {
        if (java.nio.file.Files.exists(path)) {
            try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(path)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            java.nio.file.Files.delete(p);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
            }
        }
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

    /**
     * Cleans up the shared Gradle test home directory.
     * Should be called after all tests complete.
     */
    public static void cleanupTestHome() throws IOException {
        deleteDirectory(GRADLE_TEST_HOME);
    }
    
    /**
     * Copies a directory recursively.
     */
    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (java.util.stream.Stream<Path> stream = Files.walk(source)) {
            stream.forEach(path -> {
                try {
                    Path targetPath = target.resolve(source.relativize(path));
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(path, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to copy directory", e);
                }
            });
        }
    }
}
