package io.llmcompactor.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuildErrorTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void shouldCreateWithAllFields() {
    BuildError error =
        new BuildError(
            "TestFailure", "src/Test.java", Arrays.asList(10, 11), "Fail message", "stack trace",
            150.0, "test logs");

    assertThat(error.type()).isEqualTo("TestFailure");
    assertThat(error.file()).isEqualTo("src/Test.java");
    assertThat(error.lines()).containsExactly(10, 11);
    assertThat(error.message()).isEqualTo("Fail message");
    assertThat(error.stackTrace()).isEqualTo("stack trace");
    assertThat(error.testDuration()).isEqualTo(150.0);
    assertThat(error.testLogs()).isEqualTo("test logs");
  }

  @Test
  void shouldCreateWithSingleLine() {
    BuildError error = new BuildError("Type", "File.java", 5, "Msg", "Stack");

    assertThat(error.lines()).containsExactly(5);
    assertThat(error.testDuration()).isEqualTo(0.0);
    assertThat(error.testLogs()).isNull();
  }

  @Test
  void shouldCreateWithTestDuration() {
    BuildError error = new BuildError("Type", "File.java", 5, "Msg", "Stack", 100.0);

    assertThat(error.testDuration()).isEqualTo(100.0);
    assertThat(error.testLogs()).isNull();
  }

  @Test
  void shouldCreateWithTestLogs() {
    BuildError error =
        new BuildError("Type", "File.java", 5, "Msg", "Stack", 100.0, "System.out: test");

    assertThat(error.testLogs()).isEqualTo("System.out: test");
  }

  @Test
  void shouldReturnEmptyListForNullLines() {
    BuildError error =
        new BuildError("Type", "File.java", null, "Msg", "Stack", 0.0, null);

    assertThat(error.lines()).isEmpty();
  }

  @Test
  void shouldGetJacksonProperties() {
    BuildError error = new BuildError("Type", "File.java", 5, "Msg", "Stack", 100.0, "logs");

    assertThat(error.getType()).isEqualTo("Type");
    assertThat(error.getFile()).isEqualTo("File.java");
    assertThat(error.getLines()).containsExactly(5);
    assertThat(error.getMessage()).isEqualTo("Msg");
    assertThat(error.getStackTrace()).isEqualTo("Stack");
    assertThat(error.getTestDuration()).isEqualTo(100.0);
    assertThat(error.getTestLogs()).isEqualTo("logs");
  }

  @Test
  void shouldGetTestLogsAsArray() {
    String multiLineLogs =
        "12:34:56.789 [main] INFO  Test - Message 1\n"
            + "12:34:56.790 [main] INFO  Test - Message 2\n"
            + "SLF4J: Noise";

    BuildError error = new BuildError("Type", "File.java", 1, "Msg", "Stack", 0.0, multiLineLogs);

    List<String> logsArray = error.getTestLogsAsArray();

    // SLF4J lines are filtered, timestamps/threads/levels stripped, but message content kept
    assertThat(logsArray).hasSize(2);
    assertThat(logsArray.get(0)).contains("Message 1");
    assertThat(logsArray.get(1)).contains("Message 2");
  }

  @Test
  void shouldReturnEmptyArrayForNullLogs() {
    BuildError error = new BuildError("Type", "File.java", 1, "Msg", "Stack");

    assertThat(error.getTestLogsAsArray()).isEmpty();
  }

  @Test
  void shouldEqualAndHashCode() {
    BuildError error1 =
        new BuildError("Type", "File.java", 5, "Msg", "Stack", 100.0, "logs");
    BuildError error2 =
        new BuildError("Type", "File.java", 5, "Msg", "Stack", 100.0, "logs");
    BuildError error3 =
        new BuildError("Type", "File.java", 5, "Different", "Stack", 100.0, "logs");

    assertThat(error1).isEqualTo(error2);
    assertThat(error1.hashCode()).isEqualTo(error2.hashCode());
    assertThat(error1).isNotEqualTo(error3);
  }

  @Test
  void shouldToString() {
    BuildError error = new BuildError("Type", "File.java", 5, "Msg", "Stack");

    String str = error.toString();

    assertThat(str).contains("Type");
    assertThat(str).contains("File.java");
    assertThat(str).contains("testLogs=none");
  }

  @Test
  void shouldSerializeToJson() throws Exception {
    BuildError error =
        new BuildError("TestFailure", "Test.java", 10, "Failure message", "stack", 200.0, "logs");

    String json = mapper.writeValueAsString(error);

    // type is @JsonIgnore, so it won't be in JSON
    assertThat(json).contains("\"file\":\"Test.java\"");
    assertThat(json).contains("\"lines\":[10]");
    assertThat(json).contains("\"message\":\"Failure message\"");
    assertThat(json).contains("\"testDuration\":200.0");
    assertThat(json).contains("\"testLogs\":[\"logs\"]");
  }

  @Test
  void shouldOmitEmptyTestLogsFromJson() throws Exception {
    BuildError error = new BuildError("Type", "File.java", 1, "Msg", "Stack");

    String json = mapper.writeValueAsString(error);

    assertThat(json).doesNotContain("\"testLogs\"");
  }

  @Test
  void shouldOmitZeroTestDurationFromJson() throws Exception {
    BuildError error = new BuildError("Type", "File.java", 1, "Msg", "Stack", 0.0);

    String json = mapper.writeValueAsString(error);

    assertThat(json).doesNotContain("\"testDuration\"");
  }
}
