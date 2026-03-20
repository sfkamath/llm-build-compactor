package io.llmcompactor.core.parser;

import static org.assertj.core.api.Assertions.assertThat;

import io.llmcompactor.core.BuildError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SurefireParserTest {

  @TempDir Path tempDir;

  @Test
  void shouldParseSurefireReports() throws IOException {
    Path reportsDir = tempDir.resolve("surefire-reports");
    Files.createDirectories(reportsDir);

    String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<testsuite name=\"io.llmcompactor.testbed.OrderServiceTest\" tests=\"1\" failures=\"1\" errors=\"0\" skipped=\"0\" time=\"0.05\">\n"
            + "  <testcase name=\"testOrderProcessing\" classname=\"io.llmcompactor.testbed.OrderServiceTest\" time=\"0.05\">\n"
            + "    <failure message=\"Order validation failed\" type=\"java.lang.RuntimeException\">\n"
            + "java.lang.RuntimeException: Order validation failed\n"
            + "at io.llmcompactor.testbed.OrderService.process(OrderService.java:15)\n"
            + "at io.llmcompactor.testbed.OrderServiceTest.testOrderProcessing(OrderServiceTest.java:10)\n"
            + "    </failure>\n"
            + "  </testcase>\n"
            + "</testsuite>";

    Files.write(
        reportsDir.resolve("TEST-io.llmcompactor.testbed.OrderServiceTest.xml"),
        xml.getBytes(),
        StandardOpenOption.CREATE);

    TestResult result = SurefireParser.parse(tempDir, true, Collections.emptyList(), 0);

    assertThat(result.testsRun()).isEqualTo(1);
    assertThat(result.failures()).isEqualTo(1);
    assertThat(result.errors()).hasSize(1);
    assertThat(result.allDurations()).containsExactly(0.05);

    BuildError error = result.errors().get(0);
    assertThat(error.type()).isEqualTo("java.lang.RuntimeException");
    assertThat(error.message()).isEqualTo("java.lang.RuntimeException: Order validation failed");
    // Should identify the test file (last project frame), not the service file
    assertThat(error.file()).contains("OrderServiceTest.java");
    assertThat(error.lines()).containsExactly(10);
    assertThat(error.testDuration()).isEqualTo(0.05);
  }

  @Test
  void shouldParseErrorsInReports() throws IOException {
    Path reportsDir = tempDir.resolve("surefire-reports");
    Files.createDirectories(reportsDir);

    String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<testsuite name=\"io.llmcompactor.testbed.OrderServiceTest\" tests=\"1\" failures=\"0\" errors=\"1\" skipped=\"0\" time=\"0.05\">\n"
            + "  <testcase name=\"testOrderProcessing\" classname=\"io.llmcompactor.testbed.OrderServiceTest\" time=\"0.05\">\n"
            + "    <error message=\"Critical error\" type=\"java.lang.NullPointerException\">\n"
            + "java.lang.NullPointerException: Critical error\n"
            + "at io.llmcompactor.testbed.OrderService.process(OrderService.java:20)\n"
            + "    </error>\n"
            + "  </testcase>\n"
            + "</testsuite>";

    Files.write(reportsDir.resolve("TEST-error.xml"), xml.getBytes(), StandardOpenOption.CREATE);

    TestResult result = SurefireParser.parse(tempDir, true, Collections.emptyList(), 0);

    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().get(0).type()).isEqualTo("java.lang.NullPointerException");
  }

  @Test
  void shouldParseBothSurefireAndFailsafeReports() throws IOException {
    Path surefireDir = tempDir.resolve("surefire-reports");
    Path failsafeDir = tempDir.resolve("failsafe-reports");
    Files.createDirectories(surefireDir);
    Files.createDirectories(failsafeDir);

    String unitXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<testsuite name=\"UnitTest\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\"0.05\">\n"
            + "  <testcase name=\"test\" classname=\"UnitTest\" time=\"0.05\"/>\n"
            + "</testsuite>";

    String itXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<testsuite name=\"IntegrationTest\" tests=\"2\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\"0.05\">\n"
            + "  <testcase name=\"test\" classname=\"IntegrationTest\" time=\"0.05\"/>\n"
            + "  <testcase name=\"test2\" classname=\"IntegrationTest\" time=\"0.05\"/>\n"
            + "</testsuite>";

    Files.write(
        surefireDir.resolve("TEST-unit.xml"), unitXml.getBytes(), StandardOpenOption.CREATE);
    Files.write(failsafeDir.resolve("TEST-it.xml"), itXml.getBytes(), StandardOpenOption.CREATE);

    TestResult result = SurefireParser.parse(tempDir, true, Collections.emptyList(), 0);

    // Should count all tests from both directories (1 + 2 = 3)
    assertThat(result.testsRun()).isEqualTo(3);
  }

  @Test
  void shouldIgnoreStaleReports() throws IOException {
    Path reportsDir = tempDir.resolve("surefire-reports");
    Files.createDirectories(reportsDir);

    String oldXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<testsuite name=\"OldTest\" tests=\"10\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\"0.05\">\n"
            + "  <testcase name=\"test\" classname=\"OldTest\" time=\"0.05\"/>\n"
            + "</testsuite>";

    Path oldFile = reportsDir.resolve("TEST-old.xml");
    Files.write(oldFile, oldXml.getBytes(), StandardOpenOption.CREATE);
    // Set modification time to 1 hour ago
    oldFile.toFile().setLastModified(System.currentTimeMillis() - 3600_000);

    String newXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<testsuite name=\"NewTest\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\"0.05\">\n"
            + "  <testcase name=\"test\" classname=\"NewTest\" time=\"0.05\"/>\n"
            + "</testsuite>";

    Path newFile = reportsDir.resolve("TEST-new.xml");
    Files.write(newFile, newXml.getBytes(), StandardOpenOption.CREATE);

    long sessionStart = System.currentTimeMillis() - 10_000;
    TestResult result = SurefireParser.parse(tempDir, true, Collections.emptyList(), sessionStart);

    // Should only count the 1 test from the new file, ignoring the 10 from the old file
    assertThat(result.testsRun()).isEqualTo(1);
  }

  @Test
  void shouldFilterNettyStackFramesAndIdentifyTestFile() throws IOException {
    // Reproduces the bug where Netty frames were incorrectly identified as the source file
    // Bug: file was "src/test/java/io/netty/channel/SimpleChannelInboundHandler.java"
    // Expected: file should be the actual test class "mcp/rest/ToolCallStatsControllerIT.java"
    Path reportsDir = tempDir.resolve("surefire-reports");
    Files.createDirectories(reportsDir);

    String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<testsuite name=\"mcp.rest.ToolCallStatsControllerIT\" tests=\"1\" failures=\"1\" errors=\"0\" skipped=\"0\" time=\"0.005\">\n"
            + "  <testcase name=\"testGetToolCallStats_Gemini_PerSession\" classname=\"mcp.rest.ToolCallStatsControllerIT\" time=\"0.005\">\n"
            + "    <failure message=\"io.micronaut.http.client.exceptions.HttpClientResponseException: Client '/': Not Found\" type=\"io.micronaut.http.client.exceptions.HttpClientResponseException\">\n"
            + "io.micronaut.http.client.exceptions.HttpClientResponseException: Client '/': Not Found\n"
            + "at io.netty.channel.SimpleChannelInboundHandler.channelRead(SimpleChannelInboundHandler.java:99)\n"
            + "at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:356)\n"
            + "at io.netty.handler.codec.http.HttpContentDecoder.decode(HttpContentDecoder.java:170)\n"
            + "at io.micronaut.http.client.HttpClientFilter.lambda$doRequest$0(HttpClientFilter.java:52)\n"
            + "at mcp.rest.ToolCallStatsControllerIT.testGetToolCallStats_Gemini_PerSession(ToolCallStatsControllerIT.java:166)\n"
            + "    </failure>\n"
            + "  </testcase>\n"
            + "</testsuite>";

    Files.write(
        reportsDir.resolve("TEST-mcp.rest.ToolCallStatsControllerIT.xml"),
        xml.getBytes(),
        StandardOpenOption.CREATE);

    TestResult result = SurefireParser.parse(tempDir, true, Collections.emptyList(), 0);

    assertThat(result.failures()).isEqualTo(1);
    assertThat(result.errors()).hasSize(1);

    BuildError error = result.errors().get(0);
    assertThat(error.type())
        .isEqualTo("io.micronaut.http.client.exceptions.HttpClientResponseException");
    // Should identify the test file, not the Netty handler
    assertThat(error.file()).contains("ToolCallStatsControllerIT.java");
    assertThat(error.file()).doesNotContain("io/netty");
    // Should point to line 166 (the test method), not line 99 (Netty handler)
    assertThat(error.lines()).containsExactly(166);
    // Netty frames should be filtered from stack trace
    assertThat(error.stackTrace()).doesNotContain("io.netty");
  }

  @Test
  void shouldUseLastProjectFrameForFileDetection() throws IOException {
    // When there are multiple project frames, use the last one (the actual test method)
    Path reportsDir = tempDir.resolve("surefire-reports");
    Files.createDirectories(reportsDir);

    String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<testsuite name=\"com.example.ServiceTest\" tests=\"1\" failures=\"1\" errors=\"0\" skipped=\"0\" time=\"0.01\">\n"
            + "  <testcase name=\"testWithMultipleProjectFrames\" classname=\"com.example.ServiceTest\" time=\"0.01\">\n"
            + "    <failure message=\"Assertion failed\" type=\"org.opentest4j.AssertionFailedError\">\n"
            + "org.opentest4j.AssertionFailedError: Assertion failed\n"
            + "at com.example.Service.process(Service.java:25)\n"
            + "at com.example.ServiceHelper.doWork(ServiceHelper.java:42)\n"
            + "at com.example.ServiceTest.testWithMultipleProjectFrames(ServiceTest.java:50)\n"
            + "    </failure>\n"
            + "  </testcase>\n"
            + "</testsuite>";

    Files.write(
        reportsDir.resolve("TEST-com.example.ServiceTest.xml"),
        xml.getBytes(),
        StandardOpenOption.CREATE);

    TestResult result = SurefireParser.parse(tempDir, true, Collections.emptyList(), 0);

    assertThat(result.failures()).isEqualTo(1);
    BuildError error = result.errors().get(0);

    // Should identify the test file (last project frame), not intermediate service classes
    assertThat(error.file()).contains("ServiceTest.java");
    assertThat(error.lines()).containsExactly(50);
  }

  @Test
  void shouldCompressStackTracesWithNettyFrames() throws IOException {
    // Verify that Netty frames are filtered when compressStackFrames=true
    Path reportsDir = tempDir.resolve("surefire-reports");
    Files.createDirectories(reportsDir);

    String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<testsuite name=\"com.example.MyTest\" tests=\"1\" failures=\"1\" errors=\"0\" skipped=\"0\" time=\"0.01\">\n"
            + "  <testcase name=\"testSomething\" classname=\"com.example.MyTest\" time=\"0.01\">\n"
            + "    <failure message=\"Failed\" type=\"java.lang.RuntimeException\">\n"
            + "java.lang.RuntimeException: Failed\n"
            + "at io.netty.channel.ChannelHandler.handle(ChannelHandler.java:100)\n"
            + "at io.netty.util.concurrent.EventExecutor.execute(EventExecutor.java:50)\n"
            + "at com.example.MyTest.testSomething(MyTest.java:30)\n"
            + "    </failure>\n"
            + "  </testcase>\n"
            + "</testsuite>";

    Files.write(
        reportsDir.resolve("TEST-com.example.MyTest.xml"),
        xml.getBytes(),
        StandardOpenOption.CREATE);

    // With compression enabled
    TestResult compressedResult = SurefireParser.parse(tempDir, true, Collections.emptyList(), 0);
    BuildError compressedError = compressedResult.errors().get(0);

    // Netty frames should be filtered out
    assertThat(compressedError.stackTrace()).doesNotContain("io.netty");
    assertThat(compressedError.stackTrace()).contains("com.example.MyTest");

    // Without compression
    TestResult uncompressedResult =
        SurefireParser.parse(tempDir, false, Collections.emptyList(), 0);
    BuildError uncompressedError = uncompressedResult.errors().get(0);

    // All frames should be present
    assertThat(uncompressedError.stackTrace()).contains("io.netty");
  }
}
