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

    TestResult result = SurefireParser.parse(tempDir, true, Collections.emptyList());

    assertThat(result.testsRun()).isEqualTo(1);
    assertThat(result.failures()).isEqualTo(1);
    assertThat(result.errors()).hasSize(1);

    BuildError error = result.errors().get(0);
    assertThat(error.type()).isEqualTo("java.lang.RuntimeException");
    assertThat(error.message()).isEqualTo("java.lang.RuntimeException: Order validation failed");
    assertThat(error.file()).contains("OrderService.java");
    assertThat(error.line()).isEqualTo(15);
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

    TestResult result = SurefireParser.parse(tempDir, true, Collections.emptyList());

    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().get(0).type()).isEqualTo("java.lang.NullPointerException");
  }

  @Test
  void shouldHandleEmptyReportsDir() {
    TestResult result = SurefireParser.parse(tempDir, true, Collections.emptyList());
    assertThat(result.testsRun()).isZero();
    assertThat(result.failures()).isZero();
    assertThat(result.errors()).isEmpty();
  }
}
