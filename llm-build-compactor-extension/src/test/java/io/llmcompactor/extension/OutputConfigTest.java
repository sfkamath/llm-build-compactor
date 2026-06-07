package io.llmcompactor.extension;

import static org.assertj.core.api.Assertions.assertThat;

import io.llmcompactor.core.CompactorConfig;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class OutputConfigTest {

  @Test
  void defaultsMatchCompactorDefaults() {
    OutputConfig config = OutputConfig.resolve(new PropertyResolver(null, null));
    assertThat(config.outputAsJson()).isEqualTo(CompactorConfig.DEFAULT_OUTPUT_AS_JSON);
    assertThat(config.compressStackFrames())
        .isEqualTo(CompactorConfig.DEFAULT_COMPRESS_STACK_FRAMES);
    assertThat(config.showFixTargets()).isEqualTo(CompactorConfig.DEFAULT_SHOW_FIX_TARGETS);
    assertThat(config.showFailedTestLogs())
        .isEqualTo(CompactorConfig.DEFAULT_SHOW_FAILED_TEST_LOGS);
    assertThat(config.showSlowTests()).isEqualTo(CompactorConfig.DEFAULT_SHOW_SLOW_TESTS);
    assertThat(config.showRecentChanges()).isEqualTo(CompactorConfig.DEFAULT_SHOW_RECENT_CHANGES);
  }

  @Test
  void humanModeForcesFalseOutputAsJson() {
    Properties props = new Properties();
    props.setProperty("llmCompactor.mode", "human");
    CompactorConfig config = OutputConfig.resolve(new PropertyResolver(null, props)).resolved();
    assertThat(config.outputAsJson()).isFalse();
  }

  @Test
  void agentModeForcesTrueOutputAsJson() {
    Properties props = new Properties();
    props.setProperty("llmCompactor.mode", "agent");
    props.setProperty("llmCompactor.outputAsJson", "false");
    CompactorConfig config = OutputConfig.resolve(new PropertyResolver(null, props)).resolved();
    assertThat(config.outputAsJson()).isTrue();
  }

  @Test
  void debugModeForcesTrueOutputAsJson() {
    Properties props = new Properties();
    props.setProperty("llmCompactor.mode", "debug");
    CompactorConfig config = OutputConfig.resolve(new PropertyResolver(null, props)).resolved();
    assertThat(config.outputAsJson()).isTrue();
    assertThat(config.showFailedTestLogs()).isTrue();
  }

  @Test
  void unknownModeIgnored() {
    Properties props = new Properties();
    props.setProperty("llmCompactor.mode", "unknown");
    props.setProperty("llmCompactor.outputAsJson", "false");
    CompactorConfig config = OutputConfig.resolve(new PropertyResolver(null, props)).resolved();
    assertThat(config.outputAsJson()).isFalse();
  }

  @Test
  void propOverridesDefaultWhenNoMode() {
    Properties props = new Properties();
    props.setProperty("llmCompactor.outputAsJson", "false");
    OutputConfig config = OutputConfig.resolve(new PropertyResolver(null, props));
    assertThat(config.outputAsJson()).isFalse();
  }
}
