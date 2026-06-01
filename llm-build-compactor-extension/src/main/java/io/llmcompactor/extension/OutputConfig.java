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

import io.llmcompactor.core.CompactorDefaults;

/**
 * Immutable snapshot of all output-related configuration flags for a single build run.
 *
 * <p>Call {@link #resolve(PropertyResolver)} to build an instance from the current Maven session.
 * Mode presets ({@code agent}, {@code debug}, {@code human}) are applied during construction so
 * callers never need to handle them.
 */
final class OutputConfig {

  final boolean compress;
  final boolean showFailedTestLogs;
  final boolean showFixTargets;
  final boolean outputAsJson;
  final boolean showSlowTests;
  final boolean showTotalDuration;
  final boolean showDurationReport;
  final boolean showRecentChanges;
  final double testDurationThresholdMs;

  private OutputConfig(
      boolean compress,
      boolean showFailedTestLogs,
      boolean showFixTargets,
      boolean outputAsJson,
      boolean showSlowTests,
      boolean showTotalDuration,
      boolean showDurationReport,
      boolean showRecentChanges,
      double testDurationThresholdMs) {
    this.compress = compress;
    this.showFailedTestLogs = showFailedTestLogs;
    this.showFixTargets = showFixTargets;
    this.outputAsJson = outputAsJson;
    this.showSlowTests = showSlowTests;
    this.showTotalDuration = showTotalDuration;
    this.showDurationReport = showDurationReport;
    this.showRecentChanges = showRecentChanges;
    this.testDurationThresholdMs = testDurationThresholdMs;
  }

  // -------------------------------------------------------------------------
  // Factory
  // -------------------------------------------------------------------------

  static OutputConfig resolve(PropertyResolver props) {
    boolean compress =
        props.getBoolean(
            "llmCompactor.compressStackFrames", CompactorDefaults.COMPRESS_STACK_FRAMES);
    boolean showFailedTestLogs =
        props.getBoolean(
            "llmCompactor.showFailedTestLogs", CompactorDefaults.SHOW_FAILED_TEST_LOGS);
    boolean showFixTargets =
        props.getBoolean("llmCompactor.showFixTargets", CompactorDefaults.SHOW_FIX_TARGETS);
    boolean outputAsJson =
        props.getBoolean("llmCompactor.outputAsJson", CompactorDefaults.OUTPUT_AS_JSON);
    boolean showSlowTests =
        props.getBoolean("llmCompactor.showSlowTests", CompactorDefaults.SHOW_SLOW_TESTS);
    boolean showTotalDuration =
        props.getBoolean("llmCompactor.showTotalDuration", CompactorDefaults.SHOW_TOTAL_DURATION);
    boolean showDurationReport =
        props.getBoolean("llmCompactor.showDurationReport", CompactorDefaults.SHOW_DURATION_REPORT);
    boolean showRecentChanges =
        props.getBoolean("llmCompactor.showRecentChanges", CompactorDefaults.SHOW_RECENT_CHANGES);
    double testDurationThresholdMs =
        props.getDouble(
            "llmCompactor.testDurationThresholdMs", CompactorDefaults.TEST_DURATION_THRESHOLD_MS);

    String mode = props.getString("llmCompactor.mode", null);
    ModePreset preset = ModePreset.from(mode);

    return new OutputConfig(
        compress,
        preset.overrideShowFailedTestLogs(showFailedTestLogs),
        preset.overrideShowFixTargets(showFixTargets),
        preset.overrideOutputAsJson(outputAsJson),
        showSlowTests,
        showTotalDuration,
        showDurationReport,
        showRecentChanges,
        testDurationThresholdMs);
  }

  // -------------------------------------------------------------------------
  // Mode presets
  // -------------------------------------------------------------------------

  private enum ModePreset {
    NONE {
      @Override
      boolean overrideOutputAsJson(boolean v) {
        return v;
      }

      @Override
      boolean overrideShowFixTargets(boolean v) {
        return v;
      }

      @Override
      boolean overrideShowFailedTestLogs(boolean v) {
        return v;
      }
    },
    AGENT {
      @Override
      boolean overrideOutputAsJson(boolean v) {
        return true;
      }

      @Override
      boolean overrideShowFixTargets(boolean v) {
        return true;
      }

      @Override
      boolean overrideShowFailedTestLogs(boolean v) {
        return false;
      }
    },
    DEBUG {
      @Override
      boolean overrideOutputAsJson(boolean v) {
        return true;
      }

      @Override
      boolean overrideShowFixTargets(boolean v) {
        return true;
      }

      @Override
      boolean overrideShowFailedTestLogs(boolean v) {
        return true;
      }
    },
    HUMAN {
      @Override
      boolean overrideOutputAsJson(boolean v) {
        return false;
      }

      @Override
      boolean overrideShowFixTargets(boolean v) {
        return true;
      }

      @Override
      boolean overrideShowFailedTestLogs(boolean v) {
        return false;
      }
    };

    abstract boolean overrideOutputAsJson(boolean current);

    abstract boolean overrideShowFixTargets(boolean current);

    abstract boolean overrideShowFailedTestLogs(boolean current);

    static ModePreset from(String mode) {
      if (mode == null || mode.isEmpty()) {
        return NONE;
      }
      switch (mode.toLowerCase()) {
        case "agent":
          return AGENT;
        case "debug":
          return DEBUG;
        case "human":
          return HUMAN;
        default:
          return NONE;
      }
    }
  }
}
