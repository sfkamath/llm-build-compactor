package io.llmcompactor.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

@DisplayName("Gradle Build Output")
class GradleBuildOutputTests {

  @Test
  @DisplayName("default build reports correct counts and known intentional failures")
  void testExpectedOutput() throws Exception {
    BuildResult result =
        GradleBuild.inProject("gradle-test-project").withTask("test").execute();

    assertThat(result.summaryJson()).isNotNull();
    JsonNode tree = result.summaryTree();

    assertThat(tree.get("status").asText()).isEqualTo("FAILED");
    assertThat(tree.get("testsRun").asInt()).isGreaterThan(0);
    assertThat(tree.get("failures").asInt()).isGreaterThan(0);

    assertThat(tree.has("errors")).isTrue();
    List<String> errorFiles = errorFiles(tree);
    assertThat(errorFiles)
        .contains(
            "OrderServiceTest.java",
            "StackTraceTest.java",
            "LogIsolationTest.java",
            "PaymentIT.java",
            "OrderProcessorIT.java")
        .doesNotContain("build.gradle");
  }

  @Test
  @DisplayName("build output contains no javac Note or lint warning lines")
  void testNoLintNoise() throws Exception {
    BuildResult result =
        GradleBuild.inProject("gradle-test-project").withTask("test").execute();

    for (String line : result.output().split("\n")) {
      assertThat(line.trim())
          .as("Unexpected noise in output: %s", line)
          .doesNotStartWith("Note:")
          .doesNotMatch("warning: \\[options\\].*")
          .doesNotMatch("\\d+ tests? completed.*");
    }
  }

  @Test
  @Disabled(
      "Open finding #25: the compactor cannot suppress Gradle's failure footer on a genuinely"
          + " failed build. The footer is rendered by Gradle's logging pipeline (BuildException"
          + " Reporter / BuildResultLogger) and streamed daemon->client; it never traverses the"
          + " daemon's System.out, so neither stream-redirection nor an OutputEventListener can"
          + " stop it (proven). This assertion only ever passed vacuously because gradle-test-"
          + " project sets ignoreFailures=true (build succeeds -> no footer). Re-enable only if a"
          + " footer-suppression mechanism lands. See docs/footer-leak-investigation.md.")
  @DisplayName("build output contains no Gradle 'What went wrong' or failure block")
  void testNoGradleFailureSummary() throws Exception {
    BuildResult result =
        GradleBuild.inProject("gradle-test-project").withTask("test").execute();

    assertThat(result.output())
        .as("Gradle failure summary should be suppressed for test failures")
        .doesNotContain("* What went wrong:")
        .doesNotContain("BUILD FAILED in")
        .doesNotContain("> Run with --scan");
  }

  @Test
  @DisplayName("Params serialize under configuration cache; summary still emits")
  void testConfigurationCacheCompatible() throws Exception {
    // The single Property<DefaultCompactorConfig> on CompletionService.Params is populated by a
    // lazy provider that captures the DSL extension (BuildSummaryEmitter#buildConfig). This test
    // locks in finding #20's purpose: that snapshot must serialize for Gradle's configuration
    // cache. Run 1 stores the cache; if the provider can't serialize, Gradle prints a problems
    // banner and discards it. Run 2 reuses the stored entry (execute() deletes build/ but keeps
    // .gradle/), exercising the deserialize path.
    BuildResult first =
        GradleBuild.inProject("gradle-test-project")
            .withConfigurationCache()
            .withTask("test")
            .execute();
    assertThat(first.output())
        .as("configuration cache must store cleanly (no serialization problems)")
        .doesNotContain("problems were found storing the configuration cache")
        .doesNotContain("Configuration cache problems found");
    assertThat(first.summaryJson()).isNotNull();

    BuildResult second =
        GradleBuild.inProject("gradle-test-project")
            .withConfigurationCache()
            .withTask("test")
            .execute();
    assertThat(second.summaryJson())
        .as("summary still emits when Params are restored from the configuration cache")
        .isNotNull();
  }

  @Test
  @Disabled(
      "Open finding #25 reproduction: on a compileJava failure the compactor emits the summary"
          + " correctly but Gradle's failure footer also leaks. Same root cause as the disabled"
          + " testNoGradleFailureSummary — the footer bypasses System.out and the terminal"
          + " OutputEventRenderer always fires before any plugin-reachable listener; no clean"
          + " suppression API exists in Gradle 9.5.1. Re-enable when #25 is fixed. See"
          + " docs/footer-leak-investigation.md.")
  @DisplayName("compile failures suppress Gradle's failure footer (#25)")
  void testNoGradleFailureSummaryForCompileErrors() throws Exception {
    BuildResult result =
        GradleBuild.inProject("gradle-compile-error-project").withTask("compileJava").execute();

    // The compacted summary must still carry the compile error (already asserted by
    // testCompilationErrors) ...
    assertThat(result.summaryJson()).isNotNull();

    // ... but Gradle's own failure footer must NOT also leak alongside it.
    assertThat(result.output())
        .as("compile-failure footer should be suppressed, same as test failures")
        .doesNotContain("* What went wrong:")
        .doesNotContain("Execution failed for task ':compileJava'")
        .doesNotContain("BUILD FAILED in");
  }

  @Tag("focus")
  @Test
  @DisplayName("build correctly surfaces compilation errors")
  void testCompilationErrors() throws Exception {
    BuildResult result =
        GradleBuild.inProject("gradle-compile-error-project").withTask("compileJava").execute();

    assertThat(result.summaryJson()).isNotNull();
    JsonNode tree = result.summaryTree();

    assertThat(tree.get("status").asText()).isEqualTo("FAILED");
    assertThat(tree.get("testsRun").asInt()).isEqualTo(0);
    assertThat(tree.has("errors")).isTrue();
    
    List<String> errorFiles = errorFiles(tree);
    assertThat(errorFiles).contains("Bad.java");
    
    boolean foundIncompatibleTypes = false;
    for (JsonNode error : tree.get("errors")) {
      if (error.has("message") && error.get("message").asText().contains("incompatible types")) {
        foundIncompatibleTypes = true;
        break;
      }
    }
    assertThat(foundIncompatibleTypes).isTrue();
  }

  private static List<String> errorFiles(JsonNode tree) {
    List<String> files = new ArrayList<>();
    for (JsonNode error : tree.get("errors")) {
      if (error.has("file")) {
        files.add(Paths.get(error.get("file").asText()).getFileName().toString());
      }
    }
    return files;
  }
}
