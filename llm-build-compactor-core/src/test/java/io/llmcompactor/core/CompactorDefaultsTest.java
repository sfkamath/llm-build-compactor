package io.llmcompactor.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class CompactorDefaultsTest {

  @Test
  void shouldNotBeInstantiable() throws Exception {
    assertThat(CompactorDefaults.class.getDeclaredConstructors().length).isEqualTo(1);
  }

  @Test
  void resolveEnabledReturnsTrueWhenNeitherFlagSet() {
    assertThat(CompactorDefaults.resolveEnabled(false, null)).isTrue();
    assertThat(CompactorDefaults.resolveEnabled(false, "true")).isTrue();
    assertThat(CompactorDefaults.resolveEnabled(false, "TRUE")).isTrue();
  }

  @Test
  void resolveEnabledReturnsFalseWhenLlmcePresent() {
    assertThat(CompactorDefaults.resolveEnabled(true, null)).isFalse();
    assertThat(CompactorDefaults.resolveEnabled(true, "true")).isFalse();
    assertThat(CompactorDefaults.resolveEnabled(true, "false")).isFalse();
  }

  @Test
  void resolveEnabledReturnsFalseWhenEnabledPropertyIsFalse() {
    assertThat(CompactorDefaults.resolveEnabled(false, "false")).isFalse();
    assertThat(CompactorDefaults.resolveEnabled(false, "FALSE")).isFalse();
    assertThat(CompactorDefaults.resolveEnabled(false, "False")).isFalse();
  }

  @Test
  void nullPrintStreamDoesNotThrow() {
    PrintStream ps = CompactorDefaults.nullPrintStream();
    ps.write(65);
    ps.write(new byte[]{1, 2, 3}, 0, 2);
    ps.println("test");
    assertThat(ps.checkError()).isFalse();
  }
}
