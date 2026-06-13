package io.llmcompactor.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConfigAccessorTest {

  @Test
  void parseBooleanIgnoresCase() {
    assertThat(ConfigAccessor.parseBoolean("true")).isTrue();
    assertThat(ConfigAccessor.parseBoolean("TRUE")).isTrue();
    assertThat(ConfigAccessor.parseBoolean("True")).isTrue();
    assertThat(ConfigAccessor.parseBoolean("false")).isFalse();
    assertThat(ConfigAccessor.parseBoolean("FALSE")).isFalse();
  }

  @Test
  void parseBooleanReturnsFalseForNull() {
    assertThat(ConfigAccessor.parseBoolean(null)).isFalse();
  }

  @Test
  void parseBooleanWithDefaultUsesDefaultWhenNull() {
    assertThat(ConfigAccessor.parseBoolean("true", false)).isTrue();
    assertThat(ConfigAccessor.parseBoolean(null, true)).isTrue();
    assertThat(ConfigAccessor.parseBoolean(null, false)).isFalse();
  }

  @Test
  void parseDoubleReturnsValueForValidInput() {
    assertThat(ConfigAccessor.parseDouble("42.5", 0.0)).isEqualTo(42.5);
  }

  @Test
  void parseDoubleReturnsDefaultForNull() {
    assertThat(ConfigAccessor.parseDouble(null, 10.0)).isEqualTo(10.0);
  }

  @Test
  void parseDoubleReturnsDefaultForInvalidInput() {
    assertThat(ConfigAccessor.parseDouble("not-a-number", 5.0)).isEqualTo(5.0);
  }
}
