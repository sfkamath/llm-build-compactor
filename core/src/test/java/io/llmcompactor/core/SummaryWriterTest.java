package io.llmcompactor.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SummaryWriterTest {

  @TempDir Path tempDir;

  @Test
  void shouldWriteJsonSummary() {
    BuildSummary summary =
        new BuildSummary(
            "FAILED",
            10,
            2,
            Arrays.asList(new BuildError("TestFailure", "src/Test.java", 10, "Fail", "at frame")),
            Collections.emptyList(),
            Collections.emptyList());
    Path path = tempDir.resolve("llm-summary.json");

    SummaryWriter.write(summary, path);

    assertThat(path).exists();
    assertThat(SummaryWriter.toJson(summary))
        .contains("\"status\" : \"FAILED\"")
        .contains("\"testsRun\" : 10")
        .contains("\"failures\" : 2");
  }

  @Test
  void shouldHandleJsonSerializationError() {
    // Can't easily force an IOException from writeValueAsString unless we use a custom serializer
    // or mock,
    // but let's test the error return
    String errorJson = SummaryWriter.toJson(null);
    assertThat(errorJson).isEqualTo("{}");
  }

  @Test
  void shouldFormatHumanReadableSummary() {
    BuildSummary summary =
        new BuildSummary(
            "FAILED",
            10,
            2,
            Arrays.asList(
                new BuildError(
                    "TestFailure", "src/Test.java", 10, "Fail message", "at frame\nCaused by: x")),
            Collections.emptyList(),
            Arrays.asList("README.md"));

    String human = SummaryWriter.toHumanReadable(summary, true);

    assertThat(human)
        .contains("Status: FAILED")
        .contains("Tests Run: 10")
        .contains("Failures: 2")
        .contains("- src/Test.java:10")
        .contains("Fail message")
        .contains("at frame")
        .contains("Recent Changes:")
        .contains("- README.md");
  }
}
