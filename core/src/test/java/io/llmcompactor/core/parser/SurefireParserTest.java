package io.llmcompactor.core.parser;

import static org.assertj.core.api.Assertions.assertThat;

import io.llmcompactor.core.BuildError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    Files.writeString(reportsDir.resolve("TEST-io.llmcompactor.testbed.OrderServiceTest.xml"), xml);

    TestResult result = SurefireParser.parse(tempDir, true, Collections.emptyList(), 0);

    assertThat(result.testsRun()).isEqualTo(1);
    assertThat(result.failures()).isEqualTo(1);
    assertThat(result.errors()).hasSize(1);
    assertThat(result.allDurations()).containsExactly(0.05);

    BuildError error = result.errors().get(0);
    assertThat(error.type()).isEqualTo("java.lang.RuntimeException");
    assertThat(error.message()).isEqualTo("java.lang.RuntimeException: Order validation failed");
    assertThat(error.file()).contains("OrderService.java");
    assertThat(error.line()).isEqualTo(15);
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

    Files.writeString(reportsDir.resolve("TEST-error.xml"), xml);

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

    Files.writeString(surefireDir.resolve("TEST-unit.xml"), unitXml);
    Files.writeString(failsafeDir.resolve("TEST-it.xml"), itXml);

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
    Files.writeString(oldFile, oldXml);
    // Set modification time to 1 hour ago
    oldFile.toFile().setLastModified(System.currentTimeMillis() - 3600_000);

    String newXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<testsuite name=\"NewTest\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\"0.05\">\n"
            + "  <testcase name=\"test\" classname=\"NewTest\" time=\"0.05\"/>\n"
            + "</testsuite>";

    Path newFile = reportsDir.resolve("TEST-new.xml");
    Files.writeString(newFile, newXml);

    long sessionStart = System.currentTimeMillis() - 10_000;
    TestResult result = SurefireParser.parse(tempDir, true, Collections.emptyList(), sessionStart);

    // Should only count the 1 test from the new file, ignoring the 10 from the old file
    assertThat(result.testsRun()).isEqualTo(1);
  }
}
