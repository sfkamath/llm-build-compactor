package io.llmcompactor.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompactorDefaultsTest {

  @Test
  void shouldHaveCorrectCoreBehaviorDefaults() {
    assertThat(CompactorDefaults.ENABLED).isTrue();
    assertThat(CompactorDefaults.OUTPUT_AS_JSON).isTrue();
    assertThat(CompactorDefaults.COMPRESS_STACK_FRAMES).isTrue();
  }

  @Test
  void shouldHaveCorrectOutputContentDefaults() {
    assertThat(CompactorDefaults.SHOW_FIX_TARGETS).isTrue();
    assertThat(CompactorDefaults.SHOW_RECENT_CHANGES).isFalse();
    assertThat(CompactorDefaults.SHOW_SLOW_TESTS).isTrue();
    assertThat(CompactorDefaults.SHOW_TOTAL_DURATION).isFalse();
    assertThat(CompactorDefaults.SHOW_DURATION_REPORT).isFalse();
    assertThat(CompactorDefaults.SHOW_FAILED_TEST_LOGS).isFalse();
  }

  @Test
  void shouldHaveCorrectThresholdDefaults() {
    assertThat(CompactorDefaults.TEST_DURATION_THRESHOLD_MS).isEqualTo(100.0);
  }

  @Test
  void shouldHaveCorrectOutputPathDefault() {
    assertThat(CompactorDefaults.OUTPUT_PATH).isNull();
  }

  @Test
  void shouldNotBeInstantiable() throws Exception {
    // Verify the class cannot be instantiated via reflection
    assertThat(CompactorDefaults.class.getDeclaredConstructors().length).isEqualTo(1);
  }
}
