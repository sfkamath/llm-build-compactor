package io.llmcompactor.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Gradle Stale test-results Leak (#26)")
class GradleStaleTestResultsTests {

  private static final String STALE_FAILING_XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<testsuite name=\"io.llmcompactor.StaleSpec\" tests=\"2\" failures=\"1\" errors=\"0\""
          + " skipped=\"0\" time=\"0.5\">\n"
          + "  <testcase name=\"passes\" classname=\"io.llmcompactor.StaleSpec\" time=\"0.1\"/>\n"
          + "  <testcase name=\"fails\" classname=\"io.llmcompactor.StaleSpec\" time=\"0.4\">\n"
          + "    <failure type=\"java.lang.AssertionError\" message=\"boom\">"
          + "java.lang.AssertionError: boom\n"
          + "\tat io.llmcompactor.StaleSpec.fails(StaleSpec.java:10)</failure>\n"
          + "  </testcase>\n"
          + "</testsuite>\n";

  @Test
  // Regression guard for finding #26: CompletionService.emit() must not attribute a previous run's
  // leftover test-results/ XML to a compile-only build. Fixed by gating GradleParser on the build's
  // sessionStartTime mtime (CompletionService.java ~:240, GradleParser.parse minLastModifiedMillis).
  @DisplayName("compileJava-only build must not report a prior run's stale test failures")
  void testStaleTestResultsNotAttributedToCompileOnlyBuild() throws Exception {
    BuildResult result =
        GradleBuild.inProject("gradle-stale-results-project")
            .withStagedBuildFile(
                "test-results/test/TEST-io.llmcompactor.StaleSpec.xml", STALE_FAILING_XML)
            .withTask("compileJava")
            .execute();

    assertThat(result.summaryJson()).isNotNull();
    JsonNode tree = result.summaryTree();

    assertThat(tree.get("testsRun").asInt())
        .as("compile-only build must not adopt a prior run's test count")
        .isZero();
    assertThat(tree.get("failures").asInt())
        .as("compile-only build must not adopt a prior run's failures")
        .isZero();
  }
}
