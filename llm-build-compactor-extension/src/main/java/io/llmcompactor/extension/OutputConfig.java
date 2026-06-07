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
import io.llmcompactor.core.DefaultCompactorConfig;
import io.llmcompactor.core.parser.ParserUtils;
import java.util.List;

final class OutputConfig {

  private OutputConfig() {}

  static CompactorConfig resolve(PropertyResolver props) {
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

    return DefaultCompactorConfig.builder()
        .enabled(enabled)
        .outputPath(outputPath)
        .mode(mode)
        .compressStackFrames(compress)
        .showFailedTestLogs(showFailedTestLogs)
        .showFixTargets(showFixTargets)
        .outputAsJson(outputAsJson)
        .showSlowTests(showSlowTests)
        .showTotalDuration(showTotalDuration)
        .showDurationReport(showDurationReport)
        .showRecentChanges(showRecentChanges)
        .testDurationThresholdMs(testDurationThresholdMs)
        .stackFrameWhitelist(whitelist)
        .stackFrameBlacklist(blacklist)
        .build();
  }
}
