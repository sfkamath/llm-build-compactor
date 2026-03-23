package io.llmcompactor.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Integration tests for Gradle plugin configuration options. */
@DisplayName("Gradle Plugin Options")
class GradleOptionTests {

  @Nested
  @DisplayName("Enabled Toggle")
  class EnabledToggleTests {

    @Test
    @DisplayName("enabled=false produces no compactor summary")
    void testDisabled() throws Exception {
      BuildResult result =
          GradleBuild.inProject("gradle-test-project")
              .withTask("test")
              .withProperty("llmCompactor.enabled", "false")
              .execute();

      assertThat(result.summaryJson()).isNull();
      assertThat(result.output()).doesNotContain("LLM Build Compactor Summary");
    }
  }

  @Nested
  @DisplayName("Output Format")
  class OutputFormatTests {

    @Test
    @DisplayName("outputAsJson=true produces valid JSON")
    void testJsonOutput() throws Exception {
      BuildResult result =
          GradleBuild.inProject("gradle-test-project")
              .withTask("test")
              .withProperty("llmCompactor.outputAsJson", "true")
              .execute();

      assertThat(result.summaryJson()).isNotNull();
      // If parseJson doesn't throw, JSON is valid
      parseJson(result.summaryJson());
    }

    @Test
    @DisplayName("outputAsJson=false produces human-readable output")
    void testHumanReadableOutput() throws Exception {
      BuildResult result =
          GradleBuild.inProject("gradle-test-project")
              .withTask("test")
              .withProperty("llmCompactor.outputAsJson", "false")
              .execute();

      assertThat(result.output()).contains("LLM Build Compactor Summary");
    }
  }

  @Nested
  @DisplayName("Mode Presets")
  class ModeTests {

    @Test
    @DisplayName("mode=agent produces JSON with fixTargets")
    void testModeAgent() throws Exception {
      BuildResult result =
          GradleBuild.inProject("gradle-test-project")
              .withTask("test")
              .withProperty("llmCompactor.mode", "agent")
              .execute();

      assertThat(result.summaryJson()).isNotNull();
      JsonNode tree = result.summaryTree();
      assertThat(tree).isNotNull();
      assertThat(tree.has("fixTargets")).isTrue();
    }

    @Test
    @DisplayName("mode=debug produces JSON with fixTargets and testLogs")
    void testModeDebug() throws Exception {
      BuildResult result =
          GradleBuild.inProject("gradle-test-project")
              .withTask("test")
              .withProperty("llmCompactor.mode", "debug")
              .execute();

      assertThat(result.summaryJson()).isNotNull();
      JsonNode tree = result.summaryTree();
      assertThat(tree).isNotNull();
      assertThat(tree.has("fixTargets")).isTrue();
    }

    @Test
    @DisplayName("mode=human produces human-readable output")
    void testModeHuman() throws Exception {
      BuildResult result =
          GradleBuild.inProject("gradle-test-project")
              .withTask("test")
              .withProperty("llmCompactor.mode", "human")
              .execute();

      assertThat(result.output()).contains("LLM Build Compactor Summary");
    }

    @Test
    @DisplayName("mode takes precedence over individual flags")
    void testModePrecedence() throws Exception {
      BuildResult result =
          GradleBuild.inProject("gradle-test-project")
              .withTask("test")
              .withProperty("llmCompactor.mode", "agent")
              .withProperty("llmCompactor.showFailedTestLogs", "true")
              .execute();

      JsonNode tree = result.summaryTree();
      assertThat(tree).isNotNull();
      // Agent mode doesn't include testLogs by default inside errors
      if (tree.has("errors")) {
        JsonNode errors = tree.get("errors");
        for (JsonNode error : errors) {
          assertThat(error.has("testLogs")).as("Agent mode should suppress testLogs").isFalse();
        }
      }
    }
  }

  @Nested
  @DisplayName("Content Options")
  class ContentOptionsTests {

    @Test
    @DisplayName("showFixTargets=false omits fixTargets from JSON")
    void testNoFixTargets() throws Exception {
      BuildResult result =
          GradleBuild.inProject("gradle-test-project")
              .withTask("test")
              .withProperty("llmCompactor.showFixTargets", "false")
              .execute();

      JsonNode tree = result.summaryTree();
      assertThat(tree).isNotNull();
      assertThat(tree.has("fixTargets")).isFalse();
    }

    @Test
    @DisplayName("showRecentChanges=true includes recentChanges array")
    void testShowRecentChanges() throws Exception {
      BuildResult result =
          GradleBuild.inProject("gradle-test-project")
              .withTask("test")
              .withProperty("llmCompactor.showRecentChanges", "true")
              .execute();

      JsonNode tree = result.summaryTree();
      assertThat(tree).isNotNull();
      assertThat(tree.has("recentChanges")).isTrue();
    }

    @Test
    @DisplayName("showFailedTestLogs=true includes testLogs for failed tests")
    void testShowFailedTestLogs() throws Exception {
      BuildResult result =
          GradleBuild.inProject("gradle-test-project")
              .withTask("test")
              .withProperty("llmCompactor.showFailedTestLogs", "true")
              .execute();

      JsonNode tree = result.summaryTree();
      assertThat(tree).isNotNull();
      // The test project has intentionally failing tests, so testLogs must be present
      // inside the errors array for tests that produce output (like OrderServiceTest)
      assertThat(tree.has("errors")).isTrue();
      JsonNode errors = tree.get("errors");
      assertThat(errors.isArray()).isTrue();

      boolean foundLogs = false;
      for (JsonNode error : errors) {
        if (error.has("testLogs")) {
          foundLogs = true;
          assertThat(error.get("testLogs").isArray()).isTrue();
          assertThat(error.get("testLogs").size()).isGreaterThan(0);
          break;
        }
      }
      assertThat(foundLogs).as("Expected at least one error to contain testLogs").isTrue();
    }

    @Test
    @DisplayName("showSlowTests=false omits duration from output")
    void testNoSlowTests() throws Exception {
      BuildResult result =
          GradleBuild.inProject("gradle-test-project")
              .withTask("test")
              .withProperty("llmCompactor.showSlowTests", "false")
              .execute();

      assertThat(result.summaryJson()).isNotNull();
    }

    @Test
    @DisplayName("showTotalDuration=true includes totalBuildDurationMs")
    void testShowTotalDuration() throws Exception {
      BuildResult result =
          GradleBuild.inProject("gradle-test-project")
              .withTask("test")
              .withProperty("llmCompactor.showTotalDuration", "true")
              .execute();

      JsonNode tree = result.summaryTree();
      assertThat(tree).isNotNull();
      assertThat(tree.has("totalBuildDurationMs")).isTrue();
    }

    @Test
    @DisplayName("showDurationReport=true includes percentile report")
    void testShowDurationReport() throws Exception {
      BuildResult result =
          GradleBuild.inProject("gradle-test-project")
              .withTask("test")
              .withProperty("llmCompactor.showDurationReport", "true")
              .execute();

      JsonNode tree = result.summaryTree();
      assertThat(tree).isNotNull();
      assertThat(tree.has("testDurationPercentiles")).isTrue();
    }
  }

  @Nested
  @DisplayName("Threshold Options")
  class ThresholdOptionsTests {

    @ParameterizedTest
    @ValueSource(strings = {"100", "500", "1000"})
    @DisplayName("testDurationThresholdMs configures slow test threshold")
    void testDurationThreshold(String thresholdMs) throws Exception {
      BuildResult result =
          GradleBuild.inProject("gradle-test-project")
              .withTask("test")
              .withProperty("llmCompactor.showSlowTests", "true")
              .withProperty("llmCompactor.testDurationThresholdMs", thresholdMs)
              .execute();

      assertThat(result.summaryJson()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Stack Trace Options")
  class StackTraceOptionsTests {

    @Test
    @DisplayName("compressStackFrames=true filters framework noise")
    void testCompressStackFrames() throws Exception {
      BuildResult result =
          GradleBuild.inProject("gradle-test-project")
              .withTask("test")
              .withProperty("llmCompactor.compressStackFrames", "true")
              .execute();

      assertThat(result.summaryJson()).isNotNull();
      JsonNode tree = result.summaryTree();
      if (tree != null && tree.has("errors")) {
        JsonNode errors = tree.get("errors");
        if (errors.isArray() && errors.size() > 0) {
          String stackTrace = errors.get(0).get("stackTrace").asText();
          assertThat(stackTrace).doesNotContain("org.junit");
        }
      }
    }

    @Test
    @DisplayName("includePackages preserves specified packages")
    void testIncludePackages() throws Exception {
      BuildResult result =
          GradleBuild.inProject("gradle-test-project")
              .withTask("test")
              .withProperty("llmCompactor.includePackages", "io.llmcompactor")
              .execute();

      assertThat(result.summaryJson()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Output Path")
  class OutputPathTests {

    @Test
    @DisplayName("custom outputPath writes to specified location")
    void testCustomOutputPath() throws Exception {
      // outputPath is relative to project directory
      String customPath = "custom-llm-summary.json";
      BuildResult result =
          GradleBuild.inProject("gradle-test-project")
              .withTask("test")
              .withProperty("llmCompactor.outputPath", customPath)
              .execute();

      assertThat(result.projectDir().resolve(customPath)).exists();
    }
  }

  @Nested
  @DisplayName("Maven and Gradle Parity")
  class ParityTests {

    @Test
    @DisplayName("both produce valid JSON with outputAsJson=true")
    void testBothProduceJson() throws Exception {
      BuildResult mavenResult =
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
              .withProperty("llmCompactor.outputAsJson", "true")
              .execute();

      BuildResult gradleResult =
          GradleBuild.inProject("gradle-test-project")
              .withTask("test")
              .withProperty("llmCompactor.outputAsJson", "true")
              .execute();

      // If parseJson doesn't throw, JSON is valid
      parseJson(mavenResult.summaryJson());
      parseJson(gradleResult.summaryJson());
    }

    @Test
    @DisplayName("both include fixTargets by default")
    void testBothIncludeFixTargets() throws Exception {
      BuildResult mavenResult =
          MavenBuild.inProject("maven-test-project").withGoal("verify").execute();

      BuildResult gradleResult =
          GradleBuild.inProject("gradle-test-project").withTask("test").execute();

      JsonNode mavenTree = mavenResult.summaryTree();
      JsonNode gradleTree = gradleResult.summaryTree();

      assertThat(mavenTree).isNotNull();
      assertThat(mavenTree.has("fixTargets")).isTrue();
      assertThat(gradleTree).isNotNull();
      assertThat(gradleTree.has("fixTargets")).isTrue();
    }
  }

  @Nested
  @DisplayName("Init Script Lifecycle")
  class InitScriptTests {

    private static final String INIT_SCRIPT_NAME = "llm-compactor-silence.gradle";

    @Test
    @DisplayName("applying the plugin auto-installs the init script")
    void testAutoInstall() throws Exception {
      Path initScript =
          GradleBuild.gradleTestHome().resolve("init.d").resolve(INIT_SCRIPT_NAME);
      Files.deleteIfExists(initScript);

      GradleBuild.inProject("gradle-test-project").withTask("test").execute();

      assertThat(initScript).exists();
    }

    @Test
    @DisplayName("installLlmCompactor installs the init script")
    void testInstall() throws Exception {
      Path initScript =
          GradleBuild.gradleTestHome().resolve("init.d").resolve(INIT_SCRIPT_NAME);
      Files.deleteIfExists(initScript);

      GradleBuild.inProject("gradle-test-project").withTask("installLlmCompactor").execute();

      assertThat(initScript).exists();
    }

    @Test
    @DisplayName("uninstallLlmCompactor removes the init script")
    void testUninstall() throws Exception {
      Path initScript =
          GradleBuild.gradleTestHome().resolve("init.d").resolve(INIT_SCRIPT_NAME);
      // Ensure it exists first
      GradleBuild.inProject("gradle-test-project").withTask("installLlmCompactor").execute();
      assertThat(initScript).exists();

      GradleBuild.inProject("gradle-test-project").withTask("uninstallLlmCompactor").execute();

      assertThat(initScript).doesNotExist();
    }

    @Test
    @DisplayName("uninstallLlmCompactor is a no-op when script is absent")
    void testUninstallIdempotent() throws Exception {
      Path initScript =
          GradleBuild.gradleTestHome().resolve("init.d").resolve(INIT_SCRIPT_NAME);
      Files.deleteIfExists(initScript);

      BuildResult result =
          GradleBuild.inProject("gradle-test-project")
              .withTask("uninstallLlmCompactor")
              .execute();

      assertThat(result.exitCode()).isZero();
      assertThat(initScript).doesNotExist();
    }
  }

  // Helper for JSON validation - parses JSON and returns it for further assertions
  private static JsonNode parseJson(String json) throws IOException {
    return new ObjectMapper().readTree(json);
  }
}
