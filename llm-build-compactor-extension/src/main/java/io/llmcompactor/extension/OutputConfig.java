/*
 * Copyright 2024 Jaromir Hamala (jerrinot)
 * Copyright 2024 LLM Build Compactor Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.llmcompactor.extension;

import io.llmcompactor.core.CompactorConfig;
import io.llmcompactor.core.CompactorDefaults;
import io.llmcompactor.core.parser.ParserUtils;
import java.util.List;

final class OutputConfig implements CompactorConfig {

  private final boolean enabled;
  private final String outputPath;
  private final String mode;
  private final boolean compress;
  private final boolean showFailedTestLogs;
  private final boolean showFixTargets;
  private final boolean outputAsJson;
  private final boolean showSlowTests;
  private final boolean showTotalDuration;
  private final boolean showDurationReport;
  private final boolean showRecentChanges;
  private final double testDurationThresholdMs;
  private final List<String> stackFrameWhitelist;
  private final List<String> stackFrameBlacklist;

  private OutputConfig(
      boolean enabled,
      String outputPath,
      String mode,
      boolean compress,
      boolean showFailedTestLogs,
      boolean showFixTargets,
      boolean outputAsJson,
      boolean showSlowTests,
      boolean showTotalDuration,
      boolean showDurationReport,
      boolean showRecentChanges,
      double testDurationThresholdMs,
      List<String> stackFrameWhitelist,
      List<String> stackFrameBlacklist) {
    this.enabled = enabled;
    this.outputPath = outputPath;
    this.mode = mode;
    this.compress = compress;
    this.showFailedTestLogs = showFailedTestLogs;
    this.showFixTargets = showFixTargets;
    this.outputAsJson = outputAsJson;
    this.showSlowTests = showSlowTests;
    this.showTotalDuration = showTotalDuration;
    this.showDurationReport = showDurationReport;
    this.showRecentChanges = showRecentChanges;
    this.testDurationThresholdMs = testDurationThresholdMs;
    this.stackFrameWhitelist = stackFrameWhitelist;
    this.stackFrameBlacklist = stackFrameBlacklist;
  }

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
    return compress;
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
    return stackFrameWhitelist;
  }

  @Override
  public List<String> stackFrameBlacklist() {
    return stackFrameBlacklist;
  }

  static OutputConfig resolve(PropertyResolver props) {
    boolean llmcePresent = props.getString("llmce", null) != null;
    String enabledValue = props.getString("llmCompactor.enabled", null);
    boolean enabled = CompactorDefaults.resolveEnabled(llmcePresent, enabledValue);
    String outputPath = props.getString("llmCompactor.outputPath", null);
    String mode = props.getString("llmCompactor.mode", null);
    boolean compress =
        props.getBoolean(
            "llmCompactor.compressStackFrames", CompactorConfig.DEFAULT_COMPRESS_STACK_FRAMES);
    boolean showFailedTestLogs =
        props.getBoolean(
            "llmCompactor.showFailedTestLogs", CompactorConfig.DEFAULT_SHOW_FAILED_TEST_LOGS);
    boolean showFixTargets =
        props.getBoolean("llmCompactor.showFixTargets", CompactorConfig.DEFAULT_SHOW_FIX_TARGETS);
    boolean outputAsJson =
        props.getBoolean("llmCompactor.outputAsJson", CompactorConfig.DEFAULT_OUTPUT_AS_JSON);
    boolean showSlowTests =
        props.getBoolean("llmCompactor.showSlowTests", CompactorConfig.DEFAULT_SHOW_SLOW_TESTS);
    boolean showTotalDuration =
        props.getBoolean(
            "llmCompactor.showTotalDuration", CompactorConfig.DEFAULT_SHOW_TOTAL_DURATION);
    boolean showDurationReport =
        props.getBoolean(
            "llmCompactor.showDurationReport", CompactorConfig.DEFAULT_SHOW_DURATION_REPORT);
    boolean showRecentChanges =
        props.getBoolean(
            "llmCompactor.showRecentChanges", CompactorConfig.DEFAULT_SHOW_RECENT_CHANGES);
    double testDurationThresholdMs =
        props.getDouble(
            "llmCompactor.testDurationThresholdMs",
            CompactorConfig.DEFAULT_TEST_DURATION_THRESHOLD_MS);
    List<String> whitelist =
        ParserUtils.splitCsv(props.getString("llmCompactor.stackFrameWhitelist", null));
    List<String> blacklist =
        ParserUtils.splitCsv(props.getString("llmCompactor.stackFrameBlacklist", null));

    return new OutputConfig(
        enabled,
        outputPath,
        mode,
        compress,
        showFailedTestLogs,
        showFixTargets,
        outputAsJson,
        showSlowTests,
        showTotalDuration,
        showDurationReport,
        showRecentChanges,
        testDurationThresholdMs,
        whitelist,
        blacklist);
  }
}
