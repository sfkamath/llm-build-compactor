package io.llmcompactor.core.parser;

import static org.assertj.core.api.Assertions.assertThat;

import io.llmcompactor.core.BuildError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradleParserTest {

  @TempDir Path tempDir;

  @Test
  void shouldParseGradleTestReports() throws IOException {
    Path resultsDir = tempDir.resolve("test-results");
    Files.createDirectories(resultsDir);

    String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<testsuite name=\"io.llmcompactor.testbed.OrderServiceTest\" tests=\"1\" skipped=\"0\" failures=\"1\" errors=\"0\" timestamp=\"2024-03-21T10:00:00\" hostname=\"localhost\" time=\"0.05\">\n"
            + "  <properties/>\n"
            + "  <testcase name=\"testOrderProcessing\" classname=\"io.llmcompactor.testbed.OrderServiceTest\" time=\"0.05\">\n"
            + "    <failure message=\"java.lang.RuntimeException: Order validation failed\" type=\"java.lang.RuntimeException\">\n"
            + "java.lang.RuntimeException: Order validation failed\n"
            + "at io.llmcompactor.testbed.OrderService.process(OrderService.java:15)\n"
            + "at io.llmcompactor.testbed.OrderServiceTest.testOrderProcessing(OrderServiceTest.java:10)\n"
            + "    </failure>\n"
            + "  </testcase>\n"
            + "  <system-out><![CDATA[]]></system-out>\n"
            + "  <system-err><![CDATA[]]></system-err>\n"
            + "</testsuite>";

    Files.writeString(resultsDir.resolve("TEST-io.llmcompactor.testbed.OrderServiceTest.xml"), xml);

    TestResult result = GradleParser.parse(resultsDir, true, Collections.emptyList());

    assertThat(result.testsRun()).isEqualTo(1);
    assertThat(result.failures()).isEqualTo(1);
    assertThat(result.errors()).hasSize(1);

    BuildError error = result.errors().get(0);
    assertThat(error.type()).isEqualTo("java.lang.RuntimeException");
    assertThat(error.file()).contains("OrderServiceTest.java");
    assertThat(error.line()).isEqualTo(10);
  }
}
