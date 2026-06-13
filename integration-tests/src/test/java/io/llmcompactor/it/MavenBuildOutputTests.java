package io.llmcompactor.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Maven Build Output")
class MavenBuildOutputTests {

  @Test
  @DisplayName("default build reports correct counts and known intentional failures")
  void testExpectedOutput() throws Exception {
    BuildResult result =
        MavenBuild.inProject("maven-test-project").withGoal("verify").execute();

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
        .doesNotContain("pom.xml");
  }

  @Test
  @DisplayName("build output contains no javac Note or lint warning lines")
  void testNoLintNoise() throws Exception {
    BuildResult result =
        MavenBuild.inProject("maven-test-project").withGoal("verify").execute();

    for (String line : result.output().split("\n")) {
      assertThat(line.trim())
          .as("Unexpected lint output: %s", line)
          .doesNotStartWith("Note:")
          .doesNotMatch("warning: \\[options\\].*")
          .doesNotMatch("\\[INFO\\] Tests run:.*");
    }
  }

  @Test
  @DisplayName("build output contains no Maven failure summary block")
  void testNoMavenFailureSummary() throws Exception {
    BuildResult result =
        MavenBuild.inProject("maven-test-project").withGoal("verify").execute();

    assertThat(result.output())
        .as("Maven failure summary should be suppressed for test failures")
        .doesNotContain("[ERROR] Failed to execute goal")
        .doesNotContain("[ERROR] Re-run Maven using the -X switch")
        .doesNotContain("[ERROR] For more information about the errors");
  }

  @Test
  @DisplayName("build correctly surfaces compilation errors")
  void testCompilationErrors() throws Exception {
    BuildResult result =
        MavenBuild.inProject("maven-compile-error-project").withGoal("compile").execute();

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
