package io.llmcompactor.core.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ParserUtilsTest {

  @Test
  void shouldReturnEmptyStringForNull() {
    assertThat(ParserUtils.extractFirstLine(null)).isEmpty();
  }

  @Test
  void shouldReturnEmptyStringForEmptyInput() {
    assertThat(ParserUtils.extractFirstLine("")).isEmpty();
  }

  @Test
  void shouldReturnFirstLineOfMultilineMessage() {
    assertThat(ParserUtils.extractFirstLine("line one\nline two")).isEqualTo("line one");
  }

  @Test
  void shouldTrimWhitespace() {
    assertThat(ParserUtils.extractFirstLine("  trimmed  ")).isEqualTo("trimmed");
  }
}
