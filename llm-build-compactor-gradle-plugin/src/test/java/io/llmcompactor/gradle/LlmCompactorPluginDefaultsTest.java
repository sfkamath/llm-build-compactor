package io.llmcompactor.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import io.llmcompactor.core.CompactorConfig;
import java.nio.file.Path;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LlmCompactorPluginDefaultsTest {

  @TempDir Path tempDir;

  @Test
  void conventionDefaultsMatchCompactorDefaults() {
    System.setProperty("llmce", "true");
    try {
      Project project = ProjectBuilder.builder().withGradleUserHomeDir(tempDir.toFile()).build();
      project.getPluginManager().apply(LlmCompactorPlugin.class);
      LlmCompactorPlugin.LlmCompactorExtension ext =
          project.getExtensions().getByType(LlmCompactorPlugin.LlmCompactorExtension.class);

      assertThat(ext.getOutputAsJson().get()).isEqualTo(CompactorConfig.DEFAULT_OUTPUT_AS_JSON);
      assertThat(ext.getCompressStackFrames().get())
          .isEqualTo(CompactorConfig.DEFAULT_COMPRESS_STACK_FRAMES);
      assertThat(ext.getShowFixTargets().get()).isEqualTo(CompactorConfig.DEFAULT_SHOW_FIX_TARGETS);
      assertThat(ext.getShowRecentChanges().get())
          .isEqualTo(CompactorConfig.DEFAULT_SHOW_RECENT_CHANGES);
      assertThat(ext.getShowSlowTests().get()).isEqualTo(CompactorConfig.DEFAULT_SHOW_SLOW_TESTS);
      assertThat(ext.getShowTotalDuration().get())
          .isEqualTo(CompactorConfig.DEFAULT_SHOW_TOTAL_DURATION);
      assertThat(ext.getShowDurationReport().get())
          .isEqualTo(CompactorConfig.DEFAULT_SHOW_DURATION_REPORT);
      assertThat(ext.getShowFailedTestLogs().get())
          .isEqualTo(CompactorConfig.DEFAULT_SHOW_FAILED_TEST_LOGS);
      assertThat(ext.getTestDurationThresholdMs().get())
          .isEqualTo(CompactorConfig.DEFAULT_TEST_DURATION_THRESHOLD_MS);
    } finally {
      System.clearProperty("llmce");
    }
  }
}
