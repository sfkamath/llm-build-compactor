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
            Collections.singletonList(
                new BuildError("TestFailure", "src/Test.java", 10, "Fail", "at frame")),
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
            Collections.singletonList(
                new BuildError(
                    "TestFailure", "src/Test.java", 10, "Fail message", "at frame\nCaused by: x")),
            Collections.emptyList(),
            Collections.singletonList("README.md"));

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

    // Logger names should be PRESERVED - users need to see class names in test logs for debugging
    assertThat(
            SummaryWriter.cleanTestLogLine("12:34:56.789 [main] INFO  c.e.MyClass - Test message"))
        .isEqualTo("c.e.MyClass - Test message");

    // SLF4J prefix in message content is kept (only standalone SLF4J lines filtered)
    assertThat(
            SummaryWriter.cleanTestLogLine(
                "12:34:56.789 [main] INFO  c.e.MyClass - SLF4J: Actual message"))
        .isEqualTo("c.e.MyClass - SLF4J: Actual message");

    // Brackets in log messages should be preserved (like SLF4J {} placeholder content)
    assertThat(
            SummaryWriter.cleanTestLogLine(
                "14:02:21.911 [main] INFO  i.l.testbed.OrderServiceTest - Stubs reset. Active: [file1.json, file2.json]"))
        .isEqualTo("i.l.testbed.OrderServiceTest - Stubs reset. Active: [file1.json, file2.json]");

    // Test name in brackets should be preserved
    assertThat(
            SummaryWriter.cleanTestLogLine(
                "14:02:21.913 [main] INFO  i.l.testbed.OrderServiceTest - Test [testIdentifier] configured stubs: [file1.json, file2.json]"))
        .isEqualTo(
            "i.l.testbed.OrderServiceTest - Test [testIdentifier] configured stubs: [file1.json, file2.json]");

    // Framework stacktrace frames should be filtered (micronaut, netty, spring, etc.)
    assertThat(
            SummaryWriter.cleanTestLogLine(
                "at io.micronaut.context.AbstractExecutableMethodsDefinition$DispatchedExecutableMethod.invoke(AbstractExecutableMethodsDefinition.java:456)"))
        .isNull();
    assertThat(
            SummaryWriter.cleanTestLogLine(
                "at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)"))
        .isNull();
    assertThat(
            SummaryWriter.cleanTestLogLine(
                "at org.springframework.web.servlet.FrameworkServlet.doGet(FrameworkServlet.java:900)"))
        .isNull();
    assertThat(
            SummaryWriter.cleanTestLogLine(
                "at java.base/java.util.Optional.map(Optional.java:260)"))
        .isNull();

    // Project frames should be preserved
    assertThat(
            SummaryWriter.cleanTestLogLine(
                "at com.radioprojections.service.SessionService.toRound(SessionService.java:150)"))
        .isEqualTo(
            "at com.radioprojections.service.SessionService.toRound(SessionService.java:150)");

    // Leading tab on stacktrace frames should be converted to 2 spaces for visual hierarchy
    assertThat(
            SummaryWriter.cleanTestLogLine(
                "\tat com.radioprojections.service.SessionService.toRound(SessionService.java:150)"))
        .isEqualTo(
            "  at com.radioprojections.service.SessionService.toRound(SessionService.java:150)");

    // Framework frames with leading tab should still be filtered
    assertThat(
            SummaryWriter.cleanTestLogLine(
                "\tat io.micronaut.context.AbstractExecutableMethodsDefinition$DispatchedExecutableMethod.invoke(AbstractExecutableMethodsDefinition.java:456)"))
        .isNull();

    // Non-at lines should be preserved even if they mention framework packages
    assertThat(
            SummaryWriter.cleanTestLogLine(
                "i.m.http.server.RouteExecutor - Unexpected error occurred"))
        .isEqualTo("i.m.http.server.RouteExecutor - Unexpected error occurred");

    // java.util.logging date format (Liquibase) should be stripped and then filtered
    assertThat(SummaryWriter.cleanTestLogLine("May 23, 2026 12:52:09 AM liquibase.changelog"))
        .isNull();

    // java.util.logging level with colon should be stripped
    assertThat(SummaryWriter.cleanTestLogLine("INFO: Creating database changelog table"))
        .isEqualTo("Creating database changelog table");

    // java.util.logging level with colon and timestamp should be stripped
    assertThat(SummaryWriter.cleanTestLogLine("Oct 25, 2024 10:30:45 AM INFO: Log message"))
        .isEqualTo("Log message");

    // Liquibase class references in log lines should be filtered entirely
    assertThat(
            SummaryWriter.cleanTestLogLine(
                "May 23, 2026 12:52:09 AM liquibase.changelog INFO: Creating database changelog table"))
        .isNull();
    assertThat(
            SummaryWriter.cleanTestLogLine(
                "May 23, 2026 12:52:09 AM liquibase.lockservice INFO: Successfully released change log lock"))
        .isNull();

    // Micronaut log level configuration should be filtered
    assertThat(
            SummaryWriter.cleanTestLogLine(
                "i.m.l.PropertiesLoggingLevelsConfigurer - Setting log level 'INFO' for logger: 'io.micronaut.data'"))
        .isNull();

    // Test/runtime bootstrap noise should be filtered from failed-test logs.
    assertThat(
            SummaryWriter.cleanTestLogLine(
                "o.testcontainers.DockerClientFactory - Testcontainers version: 2.0.5"))
        .isNull();
    assertThat(
            SummaryWriter.cleanTestLogLine(
                "o.t.d.DockerClientProviderStrategy - Found Docker environment with Docker accessed via Unix socket"))
        .isNull();
    assertThat(
            SummaryWriter.cleanTestLogLine(
                "tc.mongo:latest - Container mongo:latest started in PT0.22301S"))
        .isNull();
    assertThat(
            SummaryWriter.cleanTestLogLine(
                "i.m.c.DefaultApplicationContext$RuntimeConfiguredEnvironment - Established active environments: [test]"))
        .isNull();

    // [system-out] and [system-err] should be preserved
    assertThat(SummaryWriter.cleanTestLogLine("[system-out] some output"))
        .isEqualTo("[system-out] some output");
    assertThat(SummaryWriter.cleanTestLogLine("[system-err] some error"))
        .isEqualTo("[system-err] some error");

    // Empty or null lines
    assertThat(SummaryWriter.cleanTestLogLine("")).isEqualTo("");
    assertThat(SummaryWriter.cleanTestLogLine(null)).isNull();
  }

  @Test
  void shouldFormatHumanReadableWithSlowTestsAndFixTargets() {
    SlowTest slow = new SlowTest("com.example.SlowTest", "slowMethod", 5000.0);
    FixTarget target = new FixTarget("src/Main.java", 42, "Null pointer", "if (obj == null)");

    BuildSummary summary =
        new BuildSummary(
            "FAILED",
            1,
            1,
            Collections.emptyList(),
            Collections.singletonList(target),
            Collections.emptyList(),
            10000L,
            Collections.emptyMap(),
            Collections.singletonList(slow));

    String human = SummaryWriter.toHumanReadable(summary, true);

    assertThat(human).contains("Slow Tests:");
    assertThat(human).contains("com.example.SlowTest#slowMethod (5000.00ms)");
    assertThat(human).contains("Fix Targets:");
    assertThat(human).contains("src/Main.java:42");
    assertThat(human).contains("Reason: Null pointer");
    assertThat(human).contains("Snippet:");
    assertThat(human).contains("if (obj == null)");
  }

  @Test
  void shouldCondenseWhitespaceInJson() {
    BuildSummary summary =
        new BuildSummary(
            "FAILED",
            0,
            1,
            Collections.singletonList(
                new BuildError(
                    "Error", "File.java", 1, "Message with  double  space", "stack   with   tabs")),
            Collections.emptyList(),
            Collections.emptyList());

    String json = SummaryWriter.toJson(summary);

    assertThat(json).contains("Message with double space");
    assertThat(json).contains("stack with tabs");
  }

  @Test
  void shouldStripComplexExceptionPackages() {
    assertThat(SummaryWriter.stripExceptionPackage("com.foo.Bar$InnerException: msg"))
        .isEqualTo("Bar$InnerException: msg");
    assertThat(SummaryWriter.stripExceptionPackage("a.b.c.D: message with: colons"))
        .isEqualTo("D: message with: colons");
  }

  @Test
  void toJsonHandlesNullMessageAndStackTrace() {
    BuildSummary summary =
        new BuildSummary(
            "FAILED",
            0,
            1,
            Collections.singletonList(
                new BuildError("Error", "File.java", null, null, null, 0.0, null)),
            Collections.emptyList(),
            Collections.emptyList());

    String json = SummaryWriter.toJson(summary);
    assertThat(json).contains("\"status\" : \"FAILED\"");
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
  void shouldIncludePercentilesInHumanReadable() {
    Map<String, Double> percentiles = new HashMap<>();
    percentiles.put("p50", 150.0);
    percentiles.put("p99", 950.0);
    BuildSummary summary =
        new BuildSummary(
            "SUCCESS",
            10,
            0,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            5000L,
            percentiles);

    String human = SummaryWriter.toHumanReadable(summary, false);
    assertThat(human).contains("Test Duration Percentiles (ms):");
    assertThat(human).contains("p50: 150.00");
    assertThat(human).contains("p99: 950.00");
  }

  @Test
  void shouldHandleErrorWithoutFileInHumanReadable() {
    BuildError error = new BuildError("TestFailure", null, 10, "msg", "stack");
    BuildSummary summary =
        new BuildSummary(
            "FAILED",
            0,
            1,
            Collections.singletonList(error),
            Collections.emptyList(),
            Collections.emptyList());

    String human = SummaryWriter.toHumanReadable(summary, false);
    assertThat(human).contains("TestFailure");
    assertThat(human).contains("msg");
  }

  @Test
  void shouldHandleErrorWithEmptyLinesInHumanReadable() {
    BuildError error = new BuildError("Error", "File.java", -1, "msg", "stack");
    BuildSummary summary =
        new BuildSummary(
            "FAILED",
            0,
            1,
            Collections.singletonList(error),
            Collections.emptyList(),
            Collections.emptyList());

    String human = SummaryWriter.toHumanReadable(summary, false);
    assertThat(human).contains("File.java");
    assertThat(human).doesNotContain("File.java:");
  }

  @Test
  void shouldIncludeTestLogsInHumanReadable() {
    BuildError error =
        new BuildError(
            "Error",
            "File.java",
            Collections.singletonList(10),
            "msg",
            "stack",
            0.0,
            "Test log line");
    BuildSummary summary =
        new BuildSummary(
            "FAILED",
            0,
            1,
            Collections.singletonList(error),
            Collections.emptyList(),
            Collections.emptyList());

    String human = SummaryWriter.toHumanReadable(summary, true);
    assertThat(human).contains("Test logs (File.java):");
    assertThat(human).contains("Test log line");
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
            + "12:34:56.790 [main] INFO  Test - Message 2\n"
            + "at io.micronaut.foo.Bar.baz(Bar.java:10)\n"
            + "at com.myproject.MyClass.myMethod(MyClass.java:20)";

    BuildError error = new BuildError("Type", "File.java", 1, "Msg", "Stack", 0.0, logs);
    List<String> logsArray = error.getTestLogsAsArray();

    // SLF4J lines filtered, framework frames filtered, project frames and log messages preserved
    assertThat(logsArray).hasSize(3);
    assertThat(logsArray.get(0)).isEqualTo("Test - Message 1");
    assertThat(logsArray.get(1)).isEqualTo("Test - Message 2");
    assertThat(logsArray.get(2)).isEqualTo("at com.myproject.MyClass.myMethod(MyClass.java:20)");
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
