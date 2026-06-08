package io.llmcompactor.core;

import java.util.List;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
public interface CompactorConfig {

  boolean DEFAULT_ENABLED = true;
  boolean DEFAULT_OUTPUT_AS_JSON = true;
  boolean DEFAULT_COMPRESS_STACK_FRAMES = true;
  boolean DEFAULT_SHOW_FIX_TARGETS = true;
  boolean DEFAULT_SHOW_RECENT_CHANGES = false;
  boolean DEFAULT_SHOW_SLOW_TESTS = true;
  boolean DEFAULT_SHOW_TOTAL_DURATION = false;
  boolean DEFAULT_SHOW_DURATION_REPORT = false;
  boolean DEFAULT_SHOW_FAILED_TEST_LOGS = true;
  double DEFAULT_TEST_DURATION_THRESHOLD_MS = 100.0;

  boolean enabled();

  String outputPath();

  String mode();

  boolean outputAsJson();

  boolean compressStackFrames();

  boolean showFixTargets();

  boolean showRecentChanges();

  boolean showSlowTests();

  boolean showTotalDuration();

  boolean showDurationReport();

  boolean showFailedTestLogs();

  double testDurationThresholdMs();

  List<String> stackFrameWhitelist();

  List<String> stackFrameBlacklist();

  default CompactorConfig resolved() {
    ModePreset preset = ModePreset.from(mode());
    CompactorConfig base = this;
    return new CompactorConfig() {
      public boolean enabled() {
        return base.enabled();
      }

      public String outputPath() {
        return base.outputPath();
      }

      public String mode() {
        return base.mode();
      }

      public boolean outputAsJson() {
        return preset.overrideOutputAsJson(base.outputAsJson());
      }

      public boolean compressStackFrames() {
        return base.compressStackFrames();
      }

      public boolean showFixTargets() {
        return preset.overrideShowFixTargets(base.showFixTargets());
      }

      public boolean showRecentChanges() {
        return base.showRecentChanges();
      }

      public boolean showSlowTests() {
        return base.showSlowTests();
      }

      public boolean showTotalDuration() {
        return base.showTotalDuration();
      }

      public boolean showDurationReport() {
        return base.showDurationReport();
      }

      public boolean showFailedTestLogs() {
        return preset.overrideShowFailedTestLogs(base.showFailedTestLogs());
      }

      public double testDurationThresholdMs() {
        return base.testDurationThresholdMs();
      }

      public List<String> stackFrameWhitelist() {
        return base.stackFrameWhitelist();
      }

      public List<String> stackFrameBlacklist() {
        return base.stackFrameBlacklist();
      }
    };
  }
}
