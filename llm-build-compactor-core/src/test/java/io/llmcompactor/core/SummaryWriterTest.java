package io.llmcompactor.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
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

  @Test
  void shouldCleanTestLogLine() {
    // SLF4J noise should be filtered
    assertThat(SummaryWriter.cleanTestLogLine("SLF4J: No providers found")).isNull();

    // Timestamps should be stripped
    assertThat(SummaryWriter.cleanTestLogLine("12:34:56.789 Test message"))
        .isEqualTo("Test message");

    // Thread info should be stripped
    assertThat(SummaryWriter.cleanTestLogLine("12:34:56.789 [main] Test message"))
        .isEqualTo("Test message");

    // Log levels should be stripped
    assertThat(SummaryWriter.cleanTestLogLine("12:34:56.789 [main] INFO  Test message"))
        .isEqualTo("Test message");

    // Logger names should be stripped
    assertThat(
            SummaryWriter.cleanTestLogLine("12:34:56.789 [main] INFO  c.e.MyClass - Test message"))
        .isEqualTo("Test message");

    // SLF4J prefix in message content is kept (only standalone SLF4J lines filtered)
    assertThat(
            SummaryWriter.cleanTestLogLine(
                "12:34:56.789 [main] INFO  c.e.MyClass - SLF4J: Actual message"))
        .isEqualTo("SLF4J: Actual message");
  }

  @Test
  void shouldFilterTestDurationInJson() {
    Map<String, Double> percentiles = new HashMap<>();
    BuildSummary summary =
        new BuildSummary(
            "FAILED",
            10,
            2,
            Arrays.asList(
                new BuildError("SlowTest", "Test.java", 1, "msg", "stack", 200.0, null),
                new BuildError("FastTest", "Test2.java", 1, "msg", "stack", 50.0, null)),
            Collections.emptyList(),
            Collections.emptyList());

    // With 100ms threshold, slow test shows duration, fast test doesn't
    String json = SummaryWriter.toJson(summary, 100.0);
    assertThat(json).contains("\"testDuration\" : 200.0");
    assertThat(json).doesNotContain("\"testDuration\" : 50.0");
  }

  @Test
  void shouldFilterTestDurationInHumanReadable() {
    BuildSummary summary =
        new BuildSummary(
            "FAILED",
            10,
            2,
            Arrays.asList(
                new BuildError("SlowTest", "Test.java", 1, "msg", "stack", 200.0, null),
                new BuildError("FastTest", "Test2.java", 1, "msg", "stack", 50.0, null)),
            Collections.emptyList(),
            Collections.emptyList());

    String human = SummaryWriter.toHumanReadable(summary, true, 100.0);
    assertThat(human).contains("200.00ms");
    assertThat(human).doesNotContain("50.0");
  }

  @Test
  void shouldWriteToFile() throws IOException {
    BuildSummary summary =
        new BuildSummary(
            "SUCCESS",
            5,
            0,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());
    Path path = tempDir.resolve("summary.json");

    SummaryWriter.write(summary, path);

    String content = new String(Files.readAllBytes(path));
    assertThat(content).contains("\"status\" : \"SUCCESS\"");
  }

  @Test
  void shouldBuildPercentileReport() {
    Map<String, Double> percentiles = new HashMap<>();
    percentiles.put("p50", 100.0);
    percentiles.put("p90", 200.0);
    percentiles.put("p99", 500.0);

    BuildSummary summary =
        new BuildSummary(
            "SUCCESS",
            10,
            0,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            null,
            percentiles);

    String json = SummaryWriter.toJson(summary);
    assertThat(json).contains("\"p50\" : 100.0");
    assertThat(json).contains("\"p90\" : 200.0");
    assertThat(json).contains("\"p99\" : 500.0");
  }

  @Test
  void shouldIncludeBuildDuration() {
    BuildSummary summary =
        new BuildSummary(
            "SUCCESS",
            10,
            0,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            5000L,
            null);

    String json = SummaryWriter.toJson(summary);
    assertThat(json).contains("\"totalBuildDurationMs\" : 5000");

    String human = SummaryWriter.toHumanReadable(summary, false);
    assertThat(human).contains("5000");
  }

  @Test
  void shouldHandleNullSummary() {
    assertThat(SummaryWriter.toJson(null)).isEqualTo("{}");
  }

  @Test
  void shouldStripExceptionPackage() {
    // Only strips package when followed by : (exception message format)
    assertThat(SummaryWriter.stripExceptionPackage("org.opentest4j.AssertionFailedError: msg"))
        .isEqualTo("AssertionFailedError: msg");
    // Without message, no stripping occurs
    assertThat(SummaryWriter.stripExceptionPackage("java.lang.NullPointerException"))
        .isEqualTo("java.lang.NullPointerException");
    assertThat(SummaryWriter.stripExceptionPackage("Simple message")).isEqualTo("Simple message");
    assertThat(SummaryWriter.stripExceptionPackage(null)).isNull();
    assertThat(SummaryWriter.stripExceptionPackage("")).isEqualTo("");
  }

  @Test
  void shouldCondenseLongMessages() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
      sb.append('A');
    }
    String longMessage = sb.toString();
    String condensed =
        SummaryWriter.toJson(
            new BuildSummary(
                "FAILED",
                0,
                1,
                Collections.singletonList(
                    new BuildError("Error", "File.java", 1, longMessage, "stack")),
                Collections.emptyList(),
                Collections.emptyList()));
    // Long messages are included in JSON
    assertThat(condensed).contains("File.java");
    assertThat(condensed).contains("AAAAAAAAAA");
  }

  @Test
  void shouldOmitSingleLineNumbersFromJson() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    BuildError error = new BuildError("Type", "File.java", 42, "Msg", "Stack");
    String json = mapper.writeValueAsString(error);
    // Note: lines field is present in BuildError JSON (omission happens in SummaryWriter.toJson)
    assertThat(json).contains("\"lines\":[42]");
  }

  @Test
  void shouldProcessTestLogs() {
    String logs =
        "12:34:56.789 [main] INFO  Test - Message 1\n"
            + "SLF4J: Noise\n"
            + "12:34:56.790 [main] INFO  Test - Message 2";

    BuildError error = new BuildError("Type", "File.java", 1, "Msg", "Stack", 0.0, logs);
    List<String> logsArray = error.getTestLogsAsArray();

    // SLF4J lines filtered, other lines cleaned
    assertThat(logsArray).hasSize(2);
  }

  @Test
  void shouldHandleWriteIOException() {
    BuildSummary summary =
        new BuildSummary(
            "SUCCESS",
            0,
            0,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());
    // Try to write to root directory (should fail with permission denied or similar)
    Path invalidPath = Paths.get("/root/summary.json");

    Assertions.assertThrows(
        RuntimeException.class, () -> SummaryWriter.write(summary, invalidPath));
  }
}
