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

  @Test
  void shouldSplitCsv() {
    assertThat(ParserUtils.splitCsv("a,b,c")).containsExactly("a", "b", "c");
  }

  @Test
  void shouldSplitCsvAndTrim() {
    assertThat(ParserUtils.splitCsv(" a , b,c ")).containsExactly("a", "b", "c");
  }

  @Test
  void shouldHandleEmptyOrNullCsv() {
    assertThat(ParserUtils.splitCsv(null)).isEmpty();
    assertThat(ParserUtils.splitCsv("")).isEmpty();
    assertThat(ParserUtils.splitCsv("  ")).isEmpty();
  }
}
