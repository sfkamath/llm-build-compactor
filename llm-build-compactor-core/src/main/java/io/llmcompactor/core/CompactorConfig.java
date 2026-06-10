package io.llmcompactor.core;

import java.util.List;

public interface CompactorConfig {

  boolean DEFAULT_ENABLED = true;
  boolean DEFAULT_OUTPUT_AS_JSON = true;
  boolean DEFAULT_COMPRESS_STACK_FRAMES = true;
  boolean DEFAULT_SHOW_FIX_TARGETS = true;
  boolean DEFAULT_SHOW_RECENT_CHANGES = false;
  boolean DEFAULT_SHOW_SLOW_TESTS = false;
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

  CompactorConfig resolved();
}
