package io.llmcompactor.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompactorConfigTest {

  @Test
  void shouldResolveAgentMode() {
    CompactorConfig base =
        DefaultCompactorConfig.builder()
            .mode("AGENT")
            .outputAsJson(false)
            .showFixTargets(false)
            .showFailedTestLogs(true)
            .build();
    CompactorConfig resolved = base.resolved();

    assertThat(resolved.mode()).isEqualTo("AGENT");
    assertThat(resolved.outputAsJson()).isTrue(); // Agent overrides to true
    assertThat(resolved.showFixTargets()).isTrue(); // Agent overrides to true
    assertThat(resolved.showFailedTestLogs()).isFalse(); // Agent overrides to false

    // Non-overridden fields
    assertThat(resolved.enabled()).isTrue();
    assertThat(resolved.compressStackFrames()).isTrue();
    assertThat(resolved.showSlowTests()).isFalse();
  }

  @Test
  void shouldResolveDebugMode() {
    CompactorConfig base =
        DefaultCompactorConfig.builder()
            .mode("DEBUG")
            .outputAsJson(false)
            .showFixTargets(false)
            .showFailedTestLogs(false)
            .build();
    CompactorConfig resolved = base.resolved();

    assertThat(resolved.outputAsJson()).isTrue();
    assertThat(resolved.showFixTargets()).isTrue();
    assertThat(resolved.showFailedTestLogs()).isTrue();
  }

  @Test
  void shouldResolveHumanMode() {
    CompactorConfig base =
        DefaultCompactorConfig.builder()
            .mode("HUMAN")
            .outputAsJson(true)
            .showFixTargets(false)
            .showFailedTestLogs(true)
            .build();
    CompactorConfig resolved = base.resolved();

    assertThat(resolved.outputAsJson()).isFalse();
    assertThat(resolved.showFixTargets()).isTrue();
    assertThat(resolved.showFailedTestLogs()).isFalse();
  }

  @Test
  void shouldResolveNoneMode() {
    CompactorConfig base =
        DefaultCompactorConfig.builder()
            .mode("NONE")
            .outputAsJson(false)
            .showFixTargets(false)
            .showFailedTestLogs(true)
            .build();
    CompactorConfig resolved = base.resolved();

    assertThat(resolved.outputAsJson()).isFalse();
    assertThat(resolved.showFixTargets()).isFalse();
    assertThat(resolved.showFailedTestLogs()).isTrue();
  }
}
