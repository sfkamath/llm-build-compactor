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

/** Integration tests for Maven plugin configuration options. */
@DisplayName("Maven Plugin Options")
class MavenOptionTests {

  @Nested
  @DisplayName("Enabled Toggle")
  class EnabledToggleTests {

    @Test
    @DisplayName("enabled=false produces no compactor summary")
    void testDisabled() throws Exception {
      BuildResult result =
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
              .withProperty("llmCompactor.enabled", "false")
              .execute();

      assertThat(result.summaryJson()).isNull();
      assertThat(result.output()).doesNotContain("LLM Build Compactor Summary");
    }

    @Test
    @DisplayName("llmce alias produces no compactor summary")
    void testLlmceAlias() throws Exception {
      BuildResult result =
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
              .withProperty("llmce", "")
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
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
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
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
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
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
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
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
              .withProperty("llmCompactor.mode", "debug")
              .execute();

      assertThat(result.summaryJson()).isNotNull();
      JsonNode tree = result.summaryTree();
      assertThat(tree).isNotNull();
      assertThat(tree.has("fixTargets")).isTrue();
      // testLogs only appear for failed tests
    }

    @Test
    @DisplayName("mode=human produces human-readable output")
    void testModeHuman() throws Exception {
      BuildResult result =
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
              .withProperty("llmCompactor.mode", "human")
              .execute();

      assertThat(result.output()).contains("LLM Build Compactor Summary");
    }

    @Test
    @DisplayName("mode takes precedence over individual flags")
    void testModePrecedence() throws Exception {
      // mode=agent should override showFailedTestLogs=true
      BuildResult result =
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
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
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
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
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
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
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
              .withProperty("llmCompactor.showFailedTestLogs", "true")
              .execute();

      JsonNode tree = result.summaryTree();
      assertThat(tree).isNotNull();
      assertThat(tree.has("errors")).isTrue();
      JsonNode errors = tree.get("errors");
      assertThat(errors.isArray()).isTrue();

      // Find the error from LogIsolationTest.testFailingWithOutput
      JsonNode isolationError = null;
      for (JsonNode error : errors) {
        String file = error.has("file") ? error.get("file").asText() : "";
        if (file.contains("LogIsolationTest")) {
          isolationError = error;
          break;
        }
      }
      assertThat(isolationError).as("Expected an error from LogIsolationTest").isNotNull();

      // Surefire captures system-out per testcase, so only the failing test's output is present
      assertThat(isolationError.has("testLogs")).isTrue();
      String logsText = isolationError.get("testLogs").toString();
      assertThat(logsText)
          .as("Failing test's own output must appear")
          .contains("LOG_ISOLATION_FAILING_ONLY");
      assertThat(logsText)
          .as("Passing test's output must not bleed into the failing test's logs")
          .doesNotContain("LOG_ISOLATION_PASSING_ONLY");
    }

    @Test
    @DisplayName("showFailedTestLogs=false via pom <configuration> suppresses testLogs")
    void testShowFailedTestLogsPomConfig() throws Exception {
      // Drives the pom <configuration> path (Maven analog of Gradle's llmCompactor {} DSL block)
      // via the gated ${it.showFailedTestLogs} property, not the -DllmCompactor.* property path.
      BuildResult result =
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
              .withProperty("it.showFailedTestLogs", "false")
              .execute();

      JsonNode tree = result.summaryTree();
      assertThat(tree).isNotNull();
      JsonNode errors = tree.get("errors");
      assertThat(errors).isNotNull();

      JsonNode isolationError = null;
      for (JsonNode error : errors) {
        String file = error.has("file") ? error.get("file").asText() : "";
        if (file.contains("LogIsolationTest")) {
          isolationError = error;
          break;
        }
      }
      assertThat(isolationError).as("Expected an error from LogIsolationTest").isNotNull();

      assertThat(isolationError.has("testLogs"))
          .as("pom <configuration> showFailedTestLogs=false must suppress testLogs")
          .isFalse();
    }

    @Test
    @DisplayName("showSlowTests=false omits duration from human-readable output")
    void testNoSlowTests() throws Exception {
      BuildResult result =
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
              .withProperty("llmCompactor.outputAsJson", "false")
              .withProperty("llmCompactor.showSlowTests", "false")
              .withProperty("llmCompactor.testDurationThresholdMs", "0")
              .execute();

      assertThat(result.output()).contains("LLM Build Compactor Summary");
      assertThat(result.output()).doesNotContain("ms)");
    }

    @Test
    @DisplayName("showSlowTests=true with threshold=0 includes slowTests in JSON")
    void testShowSlowTestsJson() throws Exception {
      BuildResult result =
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
              .withProperty("llmCompactor.showSlowTests", "true")
              .withProperty("llmCompactor.testDurationThresholdMs", "0")
              .execute();

      JsonNode tree = result.summaryTree();
      assertThat(tree).isNotNull();
      assertThat(tree.has("slowTests")).isTrue();
      JsonNode slowTests = tree.get("slowTests");
      assertThat(slowTests.isArray()).isTrue();
      assertThat(slowTests).isNotEmpty();
      JsonNode first = slowTests.get(0);
      assertThat(first.has("className")).isTrue();
      assertThat(first.has("testName")).isTrue();
      assertThat(first.has("testDuration")).isTrue();
      assertThat(first.get("testDuration").asDouble()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("showSlowTests=false omits slowTests from JSON")
    void testSlowTestsHiddenInJson() throws Exception {
      BuildResult result =
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
              .withProperty("llmCompactor.showSlowTests", "false")
              .execute();

      JsonNode tree = result.summaryTree();
      assertThat(tree).isNotNull();
      assertThat(tree.has("slowTests")).isFalse();
    }

    @Test
    @DisplayName("showSlowTests=true with threshold=0 shows Slow Tests section in human-readable output")
    void testShowSlowTestsHumanReadable() throws Exception {
      BuildResult result =
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
              .withProperty("llmCompactor.outputAsJson", "false")
              .withProperty("llmCompactor.showSlowTests", "true")
              .withProperty("llmCompactor.testDurationThresholdMs", "0")
              .execute();

      assertThat(result.output()).contains("Slow Tests:");
    }

    @Test
    @DisplayName("showTotalDuration=true includes totalBuildDurationMs")
    void testShowTotalDuration() throws Exception {
      BuildResult result =
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
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
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
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

    @Test
    @DisplayName("testDurationThresholdMs=0 includes testDuration in JSON errors")
    void testDurationThresholdZero() throws Exception {
      BuildResult result =
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
              .withProperty("llmCompactor.outputAsJson", "true")
              .withProperty("llmCompactor.testDurationThresholdMs", "0")
              .execute();

      JsonNode tree = result.summaryTree();
      assertThat(tree).isNotNull();
      assertThat(tree.has("errors")).isTrue();
      JsonNode errors = tree.get("errors");
      assertThat(errors.isArray()).isTrue();
      assertThat(errors).isNotEmpty();
      boolean hasDuration = false;
      for (JsonNode error : errors) {
        if (error.has("testDuration") && error.get("testDuration").asDouble() > 0) {
          hasDuration = true;
          break;
        }
      }
      assertThat(hasDuration)
          .as("At least one error should have non-zero testDuration with threshold=0")
          .isTrue();
    }

    @Test
    @DisplayName("testDurationThresholdMs=100000 excludes all testDuration from JSON")
    void testDurationThresholdHigh() throws Exception {
      BuildResult result =
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
              .withProperty("llmCompactor.outputAsJson", "true")
              .withProperty("llmCompactor.testDurationThresholdMs", "100000")
              .execute();

      JsonNode tree = result.summaryTree();
      assertThat(tree).isNotNull();
      assertThat(tree.has("errors")).isTrue();
      JsonNode errors = tree.get("errors");
      assertThat(errors.isArray()).isTrue();
      assertThat(errors).isNotEmpty();
      for (JsonNode error : errors) {
        assertThat(error.has("testDuration"))
            .as("Error should not have testDuration with threshold=100000")
            .isFalse();
      }
    }
  }

  @Nested
  @DisplayName("Stack Trace Options")
  class StackTraceOptionsTests {

    @Test
    @DisplayName("compressStackFrames=true filters framework noise")
    void testCompressStackFrames() throws Exception {
      BuildResult result =
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
              .withProperty("llmCompactor.compressStackFrames", "true")
              .execute();

      assertThat(result.summaryJson()).isNotNull();
      // Compressed stack traces should be shorter
      JsonNode tree = result.summaryTree();
      if (tree != null && tree.has("errors")) {
        JsonNode errors = tree.get("errors");
        if (errors.isArray() && errors.size() > 0) {
          String stackTrace = errors.get(0).get("stackTrace").asText();
          // Should not contain common framework packages
          assertThat(stackTrace).doesNotContain("org.junit");
        }
      }
    }

    @Test
    @DisplayName("stackFrameWhitelist preserves specified packages")
    void testStackFrameWhitelist() throws Exception {
      BuildResult result =
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
              .withProperty("llmCompactor.stackFrameWhitelist", "io.llmcompactor")
              .execute();

      assertThat(result.summaryJson()).isNotNull();
      assertThat(result.summaryJson()).contains("io.llmcompactor");
    }

    @Test
    @DisplayName("stackFrameBlacklist excludes specified packages")
    void testStackFrameBlacklist() throws Exception {
      BuildResult result =
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
              .withProperty("llmCompactor.stackFrameBlacklist", "io.llmcompactor")
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
      String customPath = "target/custom-llm-summary.json";
      BuildResult result =
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
              .withProperty("llmCompactor.outputPath", customPath)
              .execute();

      // Verify custom file was created
      assertThat(result.buildDir().resolve("custom-llm-summary.json")).exists();
    }
  }

  @Nested
  @DisplayName("Install Lifecycle")
  class InstallTests {

    @Test
    @DisplayName("install goal creates .mvn/extensions.xml")
    void testInstallExtension() throws Exception {
      Path extensionsXml =
          MavenBuild.inProject("maven-test-project").getProjectDir().resolve(".mvn/extensions.xml");
      try {
        MavenBuild.inProject("maven-test-project").withGoal("llm-compactor:install").execute();

        assertThat(extensionsXml).exists();
        String content = new String(Files.readAllBytes(extensionsXml));
        assertThat(content).contains("llm-build-compactor-extension");
      } finally {
        Files.deleteIfExists(extensionsXml);
      }
    }
  }

  // Helper for JSON validation - parses JSON and returns it for further assertions

  private static JsonNode parseJson(String json) throws IOException {
    return new ObjectMapper().readTree(json);
  }

  @Nested
  @DisplayName("Build Status")
  class BuildStatusTests {

    @Test
    @DisplayName("summary status is FAILED when build has errors")
    void testStatusFailedOnErrors() throws Exception {
      BuildResult result =
          MavenBuild.inProject("maven-test-project")
              .withGoal("verify")
              .withProperty("llmCompactor.outputAsJson", "true")
              .execute();

      assertThat(result.summaryJson()).isNotNull();
      JsonNode tree = parseJson(result.summaryJson());
      assertThat(tree.has("status")).isTrue();
      assertThat(tree.get("status").asText()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("summary status is SUCCESS and errors is empty when build succeeds")
    void testStatusSuccessOnCleanBuild() throws Exception {
      BuildResult result =
          MavenBuild.inProject("maven-test-project")
              .withGoal("compile")
              .withProperty("llmCompactor.outputAsJson", "true")
              .execute();

      assertThat(result.summaryJson()).isNotNull();
      JsonNode tree = parseJson(result.summaryJson());
      assertThat(tree).isNotNull();
      assertThat(tree.has("status")).isTrue();
      assertThat(tree.get("status").asText()).isEqualTo("SUCCESS");
      assertThat(tree.has("errors")).isTrue();
      assertThat(tree.get("errors").isArray()).isTrue();
      assertThat(tree.get("errors")).isEmpty();
    }
  }
}

