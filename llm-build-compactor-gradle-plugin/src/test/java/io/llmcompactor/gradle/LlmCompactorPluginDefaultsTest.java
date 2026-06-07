package io.llmcompactor.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import io.llmcompactor.core.CompactorDefaults;
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

      assertThat(ext.getOutputAsJson().get()).isEqualTo(CompactorDefaults.OUTPUT_AS_JSON);
      assertThat(ext.getCompressStackFrames().get())
          .isEqualTo(CompactorDefaults.COMPRESS_STACK_FRAMES);
      assertThat(ext.getShowFixTargets().get()).isEqualTo(CompactorDefaults.SHOW_FIX_TARGETS);
      assertThat(ext.getShowRecentChanges().get()).isEqualTo(CompactorDefaults.SHOW_RECENT_CHANGES);
      assertThat(ext.getShowSlowTests().get()).isEqualTo(CompactorDefaults.SHOW_SLOW_TESTS);
      assertThat(ext.getShowTotalDuration().get()).isEqualTo(CompactorDefaults.SHOW_TOTAL_DURATION);
      assertThat(ext.getShowDurationReport().get())
          .isEqualTo(CompactorDefaults.SHOW_DURATION_REPORT);
      assertThat(ext.getShowFailedTestLogs().get())
          .isEqualTo(CompactorDefaults.SHOW_FAILED_TEST_LOGS);
      assertThat(ext.getTestDurationThresholdMs().get())
          .isEqualTo(CompactorDefaults.TEST_DURATION_THRESHOLD_MS);
    } finally {
      System.clearProperty("llmce");
    }
  }
}
