package io.llmcompactor.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Default implementation of {@link CompactorConfig} with a builder. */
public final class DefaultCompactorConfig implements CompactorConfig {
  private boolean enabled = DEFAULT_ENABLED;
  private String outputPath;
  private String mode;
  private boolean outputAsJson = DEFAULT_OUTPUT_AS_JSON;
  private boolean compressStackFrames = DEFAULT_COMPRESS_STACK_FRAMES;
  private boolean showFixTargets = DEFAULT_SHOW_FIX_TARGETS;
  private boolean showRecentChanges = DEFAULT_SHOW_RECENT_CHANGES;
  private boolean showSlowTests = DEFAULT_SHOW_SLOW_TESTS;
  private boolean showTotalDuration = DEFAULT_SHOW_TOTAL_DURATION;
  private boolean showDurationReport = DEFAULT_SHOW_DURATION_REPORT;
  private boolean showFailedTestLogs = DEFAULT_SHOW_FAILED_TEST_LOGS;
  private double testDurationThresholdMs = DEFAULT_TEST_DURATION_THRESHOLD_MS;
  private List<String> stackFrameWhitelist = Collections.emptyList();
  private List<String> stackFrameBlacklist = Collections.emptyList();

  private DefaultCompactorConfig() {}

  @Override
  public boolean enabled() {
    return enabled;
  }

  @Override
  public String outputPath() {
    return outputPath;
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
    return compressStackFrames;
  }

  @Override
  public boolean showFixTargets() {
    return showFixTargets;
  }

  @Override
  public boolean showRecentChanges() {
    return showRecentChanges;
  }

  @Override
  public boolean showSlowTests() {
    return showSlowTests;
  }

  @Override
  public boolean showTotalDuration() {
    return showTotalDuration;
  }

  @Override
  public boolean showDurationReport() {
    return showDurationReport;
  }

  @Override
  public boolean showFailedTestLogs() {
    return showFailedTestLogs;
  }

  @Override
  public double testDurationThresholdMs() {
    return testDurationThresholdMs;
  }

  @Override
  public List<String> stackFrameWhitelist() {
    return Collections.unmodifiableList(stackFrameWhitelist);
  }

  @Override
  public List<String> stackFrameBlacklist() {
    return Collections.unmodifiableList(stackFrameBlacklist);
  }

  /**
   * Merges the user-supplied whitelist with auto-scanned packages from multiple projects/modules.
   * Used by both Maven and Gradle consumers to ensure project packages are always included in
   * stack-trace frames.
   */
  public static List<String> mergeWhitelist(
      List<String> userWhitelist, List<List<String>> projectScanResults) {
    List<String> result = new ArrayList<>(userWhitelist);
    for (List<String> packages : projectScanResults) {
      for (String pkg : packages) {
        if (!result.contains(pkg)) {
          result.add(pkg);
        }
      }
    }
    return result;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private final DefaultCompactorConfig config = new DefaultCompactorConfig();

    public Builder enabled(boolean enabled) {
      config.enabled = enabled;
      return this;
    }

    public Builder outputPath(String outputPath) {
      config.outputPath = outputPath;
      return this;
    }

    public Builder mode(String mode) {
      config.mode = mode;
      return this;
    }

    public Builder outputAsJson(boolean outputAsJson) {
      config.outputAsJson = outputAsJson;
      return this;
    }

    public Builder compressStackFrames(boolean compressStackFrames) {
      config.compressStackFrames = compressStackFrames;
      return this;
    }

    public Builder showFixTargets(boolean showFixTargets) {
      config.showFixTargets = showFixTargets;
      return this;
    }

    public Builder showRecentChanges(boolean showRecentChanges) {
      config.showRecentChanges = showRecentChanges;
      return this;
    }

    public Builder showSlowTests(boolean showSlowTests) {
      config.showSlowTests = showSlowTests;
      return this;
    }

    public Builder showTotalDuration(boolean showTotalDuration) {
      config.showTotalDuration = showTotalDuration;
      return this;
    }

    public Builder showDurationReport(boolean showDurationReport) {
      config.showDurationReport = showDurationReport;
      return this;
    }

    public Builder showFailedTestLogs(boolean showFailedTestLogs) {
      config.showFailedTestLogs = showFailedTestLogs;
      return this;
    }

    public Builder testDurationThresholdMs(double testDurationThresholdMs) {
      config.testDurationThresholdMs = testDurationThresholdMs;
      return this;
    }

    public Builder stackFrameWhitelist(List<String> stackFrameWhitelist) {
      config.stackFrameWhitelist =
          stackFrameWhitelist != null
              ? Collections.unmodifiableList(new ArrayList<>(stackFrameWhitelist))
              : Collections.emptyList();
      return this;
    }

    public Builder stackFrameBlacklist(List<String> stackFrameBlacklist) {
      config.stackFrameBlacklist =
          stackFrameBlacklist != null
              ? Collections.unmodifiableList(new ArrayList<>(stackFrameBlacklist))
              : Collections.emptyList();
      return this;
    }

    public CompactorConfig build() {
      return config;
    }
  }
}
