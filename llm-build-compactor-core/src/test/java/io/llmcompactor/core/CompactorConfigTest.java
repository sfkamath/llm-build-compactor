package io.llmcompactor.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompactorConfigTest {

  static class StubConfig implements CompactorConfig {
    private final String mode;
    private final boolean outputAsJson;
    private final boolean showFixTargets;
    private final boolean showFailedTestLogs;

    StubConfig(
        String mode, boolean outputAsJson, boolean showFixTargets, boolean showFailedTestLogs) {
      this.mode = mode;
      this.outputAsJson = outputAsJson;
      this.showFixTargets = showFixTargets;
      this.showFailedTestLogs = showFailedTestLogs;
    }

    @Override
    public boolean enabled() {
      return true;
    }

    @Override
    public String outputPath() {
      return null;
    }

    @Override
    public String mode() {
      return mode;
    }

    @Override
    public boolean outputAsJson() {
      return outputAsJson;
    }

    @Override
    public boolean compressStackFrames() {
      return true;
    }

    @Override
    public boolean showFixTargets() {
      return showFixTargets;
    }

    @Override
    public boolean showRecentChanges() {
      return false;
    }

    @Override
    public boolean showSlowTests() {
      return true;
    }

    @Override
    public boolean showTotalDuration() {
      return false;
    }

    @Override
    public boolean showDurationReport() {
      return false;
    }

    @Override
    public boolean showFailedTestLogs() {
      return showFailedTestLogs;
    }

    @Override
    public double testDurationThresholdMs() {
      return 100.0;
    }

    @Override
    public List<String> stackFrameWhitelist() {
      return Collections.emptyList();
    }

    @Override
    public List<String> stackFrameBlacklist() {
      return Collections.emptyList();
    }
  }

  @Test
  void shouldResolveAgentMode() {
    CompactorConfig base = new StubConfig("AGENT", false, false, true);
    CompactorConfig resolved = base.resolved();

    assertThat(resolved.mode()).isEqualTo("AGENT");
    assertThat(resolved.outputAsJson()).isTrue(); // Agent overrides to true
    assertThat(resolved.showFixTargets()).isTrue(); // Agent overrides to true
    assertThat(resolved.showFailedTestLogs()).isFalse(); // Agent overrides to false

    // Non-overridden fields
    assertThat(resolved.enabled()).isTrue();
    assertThat(resolved.compressStackFrames()).isTrue();
    assertThat(resolved.showSlowTests()).isTrue();
  }

  @Test
  void shouldResolveDebugMode() {
    CompactorConfig base = new StubConfig("DEBUG", false, false, false);
    CompactorConfig resolved = base.resolved();

    assertThat(resolved.outputAsJson()).isTrue();
    assertThat(resolved.showFixTargets()).isTrue();
    assertThat(resolved.showFailedTestLogs()).isTrue();
  }

  @Test
  void shouldResolveHumanMode() {
    CompactorConfig base = new StubConfig("HUMAN", true, false, true);
    CompactorConfig resolved = base.resolved();

    assertThat(resolved.outputAsJson()).isFalse();
    assertThat(resolved.showFixTargets()).isTrue();
    assertThat(resolved.showFailedTestLogs()).isFalse();
  }

  @Test
  void shouldResolveNoneMode() {
    CompactorConfig base = new StubConfig("NONE", false, false, true);
    CompactorConfig resolved = base.resolved();

    assertThat(resolved.outputAsJson()).isFalse();
    assertThat(resolved.showFixTargets()).isFalse();
    assertThat(resolved.showFailedTestLogs()).isTrue();
  }
}
