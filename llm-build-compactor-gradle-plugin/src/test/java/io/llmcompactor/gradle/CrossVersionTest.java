package io.llmcompactor.gradle;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CrossVersionTest {

  @TempDir Path testKitDir;

  @ParameterizedTest
  @ValueSource(strings = {"8.14.4", "9.3.0", "9.5.1"})
  void testCountLoggerSuppressedAcrossVersions(String gradleVersion) throws Exception {
    // Gradle 9.x requires Java 17+
    if (gradleVersion.startsWith("9.")) {
      String javaVersion = System.getProperty("java.version");
      Assumptions.assumeTrue(
          javaVersion != null && parsedMajor(javaVersion) >= 17,
          "Gradle " + gradleVersion + " requires Java 17+ (current: " + javaVersion + ")");
    }

    URL resource = getClass().getClassLoader().getResource("test-project");
    Path projectDir = Paths.get(resource.toURI());

    System.out.println("=== Testing Gradle " + gradleVersion + " ===");
    BuildResult result =
        GradleRunner.create()
            .withTestKitDir(testKitDir.resolve(gradleVersion.replace('.', '_')).toFile())
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withGradleVersion(gradleVersion)
            .withArguments("test", "-PenableCompactor", "--console=plain", "--info")
            .buildAndFail();

    String output = result.getOutput();
    String[] lines = output.split("\n");
    boolean found = false;
    for (String line : lines) {
      if (line.matches(".*\\d+ tests? completed.*")) {
        System.out.println("  FAIL: Found summary line: " + line.trim());
        found = true;
      }
    }
    System.out.println(
        found ? "  RESULT: FAIL (summary line present)" : "  RESULT: PASS (no summary line)");
  }

  private static int parsedMajor(String javaVersion) {
    String v = javaVersion;
    int dash = v.indexOf('-');
    if (dash != -1) v = v.substring(0, dash);
    String[] parts = v.split("\\.");
    try {
      return parts[0].equals("1") && parts.length > 1
          ? Integer.parseInt(parts[1])
          : Integer.parseInt(parts[0]);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
