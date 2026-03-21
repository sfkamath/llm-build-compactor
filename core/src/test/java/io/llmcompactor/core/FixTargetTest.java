package io.llmcompactor.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class FixTargetTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void shouldCreateWithAllFields() {
    FixTarget target = new FixTarget("src/Test.java", 10, "Fix this error", "code snippet");

    assertThat(target.file()).isEqualTo("src/Test.java");
    assertThat(target.line()).isEqualTo(10);
    assertThat(target.reason()).isEqualTo("Fix this error");
    assertThat(target.snippet()).isEqualTo("code snippet");
  }

  @Test
  void shouldGetJacksonProperties() {
    FixTarget target = new FixTarget("File.java", 5, "Reason", "snippet");

    assertThat(target.getFile()).isEqualTo("File.java");
    assertThat(target.getLine()).isEqualTo(5);
    assertThat(target.getReason()).isEqualTo("Reason");
    assertThat(target.getSnippet()).isEqualTo("snippet");
  }

  @Test
  void shouldOmitNullSnippetFromJson() throws Exception {
    FixTarget target = new FixTarget("File.java", 5, "Reason", null);

    String json = mapper.writeValueAsString(target);

    assertThat(json).contains("\"file\":\"File.java\"");
    assertThat(json).doesNotContain("\"snippet\"");
  }

  @Test
  void shouldEqualAndHashCode() {
    FixTarget target1 = new FixTarget("File.java", 5, "Reason", "snippet");
    FixTarget target2 = new FixTarget("File.java", 5, "Reason", "snippet");
    FixTarget target3 = new FixTarget("File.java", 10, "Reason", "snippet");

    assertThat(target1).isEqualTo(target2);
    assertThat(target1.hashCode()).isEqualTo(target2.hashCode());
    assertThat(target1).isNotEqualTo(target3);
  }

  @Test
  void shouldToString() {
    FixTarget target = new FixTarget("File.java", 5, "Reason", null);

    String str = target.toString();

    assertThat(str).contains("File.java");
    assertThat(str).contains("line=5");
    assertThat(str).contains("Reason");
  }

  @Test
  void shouldSerializeToJson() throws Exception {
    FixTarget target = new FixTarget("src/Test.java", 42, "Null pointer fix", "obj.toString()");

    String json = mapper.writeValueAsString(target);

    assertThat(json).contains("\"file\":\"src/Test.java\"");
    assertThat(json).contains("\"line\":42");
    assertThat(json).contains("\"reason\":\"Null pointer fix\"");
    assertThat(json).contains("\"snippet\":\"obj.toString()\"");
  }
}
