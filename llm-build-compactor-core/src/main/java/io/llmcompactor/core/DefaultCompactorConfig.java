package io.llmcompactor.core;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.experimental.Accessors;

/** Default implementation of {@link CompactorConfig} with a builder. */
@Builder
@Getter
@Accessors(fluent = true)
public final class DefaultCompactorConfig implements CompactorConfig {
  @Builder.Default private boolean enabled = DEFAULT_ENABLED;
  private String outputPath;
  private String mode;
  @Builder.Default private boolean outputAsJson = DEFAULT_OUTPUT_AS_JSON;
  @Builder.Default private boolean compressStackFrames = DEFAULT_COMPRESS_STACK_FRAMES;
  @Builder.Default private boolean showFixTargets = DEFAULT_SHOW_FIX_TARGETS;
  @Builder.Default private boolean showRecentChanges = DEFAULT_SHOW_RECENT_CHANGES;
  @Builder.Default private boolean showSlowTests = DEFAULT_SHOW_SLOW_TESTS;
  @Builder.Default private boolean showTotalDuration = DEFAULT_SHOW_TOTAL_DURATION;
  @Builder.Default private boolean showDurationReport = DEFAULT_SHOW_DURATION_REPORT;
  @Builder.Default private boolean showFailedTestLogs = DEFAULT_SHOW_FAILED_TEST_LOGS;
  @Builder.Default private double testDurationThresholdMs = DEFAULT_TEST_DURATION_THRESHOLD_MS;

  @Singular("whitelistEntry")
  private List<String> stackFrameWhitelist;

  @Singular("blacklistEntry")
  private List<String> stackFrameBlacklist;

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
}
