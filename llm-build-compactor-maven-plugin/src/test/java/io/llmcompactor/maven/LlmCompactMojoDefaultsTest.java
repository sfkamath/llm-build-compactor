package io.llmcompactor.maven;

import static org.assertj.core.api.Assertions.assertThat;

import io.llmcompactor.core.CompactorConfig;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class LlmCompactMojoDefaultsTest {

  private static boolean boolField(LlmCompactMojo mojo, String name) throws Exception {
    Field f = LlmCompactMojo.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.getBoolean(mojo);
  }

  private static double doubleField(LlmCompactMojo mojo, String name) throws Exception {
    Field f = LlmCompactMojo.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.getDouble(mojo);
  }

  @Test
  void fieldDefaultsMatchCompactorDefaults() throws Exception {
    LlmCompactMojo mojo = new LlmCompactMojo();

    assertThat(boolField(mojo, "enabled")).isEqualTo(CompactorConfig.DEFAULT_ENABLED);
    assertThat(boolField(mojo, "outputAsJson")).isEqualTo(CompactorConfig.DEFAULT_OUTPUT_AS_JSON);
    assertThat(boolField(mojo, "compressStackFrames"))
        .isEqualTo(CompactorConfig.DEFAULT_COMPRESS_STACK_FRAMES);
    assertThat(boolField(mojo, "showFixTargets"))
        .isEqualTo(CompactorConfig.DEFAULT_SHOW_FIX_TARGETS);
    assertThat(boolField(mojo, "showRecentChanges"))
        .isEqualTo(CompactorConfig.DEFAULT_SHOW_RECENT_CHANGES);
    assertThat(boolField(mojo, "showSlowTests")).isEqualTo(CompactorConfig.DEFAULT_SHOW_SLOW_TESTS);
    assertThat(boolField(mojo, "showTotalDuration"))
        .isEqualTo(CompactorConfig.DEFAULT_SHOW_TOTAL_DURATION);
    assertThat(boolField(mojo, "showDurationReport"))
        .isEqualTo(CompactorConfig.DEFAULT_SHOW_DURATION_REPORT);
    assertThat(boolField(mojo, "showFailedTestLogs"))
        .isEqualTo(CompactorConfig.DEFAULT_SHOW_FAILED_TEST_LOGS);
    assertThat(doubleField(mojo, "testDurationThresholdMs"))
        .isEqualTo(CompactorConfig.DEFAULT_TEST_DURATION_THRESHOLD_MS);
  }
}
