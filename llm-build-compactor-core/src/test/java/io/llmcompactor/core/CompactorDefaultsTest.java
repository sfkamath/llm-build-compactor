package io.llmcompactor.core;

import static org.assertj.core.api.Assertions.assertThat;

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
}
