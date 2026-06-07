package io.llmcompactor.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultCompactorConfigTest {

  @Test
  void builderSetsAllFields() {
    List<String> whitelist = Arrays.asList("com.example", "org.test");
    List<String> blacklist = Arrays.asList("java.util", "sun.misc");

    CompactorConfig config =
        DefaultCompactorConfig.builder()
            .enabled(false)
            .outputPath("custom/path.json")
            .mode("agent")
            .outputAsJson(false)
            .compressStackFrames(false)
            .showFixTargets(false)
            .showRecentChanges(true)
            .showSlowTests(false)
            .showTotalDuration(true)
            .showDurationReport(true)
            .showFailedTestLogs(false)
            .testDurationThresholdMs(500.0)
            .stackFrameWhitelist(whitelist)
            .stackFrameBlacklist(blacklist)
            .build();

    assertThat(config.enabled()).isFalse();
    assertThat(config.outputPath()).isEqualTo("custom/path.json");
    assertThat(config.mode()).isEqualTo("agent");
    assertThat(config.outputAsJson()).isFalse();
    assertThat(config.compressStackFrames()).isFalse();
    assertThat(config.showFixTargets()).isFalse();
    assertThat(config.showRecentChanges()).isTrue();
    assertThat(config.showSlowTests()).isFalse();
    assertThat(config.showTotalDuration()).isTrue();
    assertThat(config.showDurationReport()).isTrue();
    assertThat(config.showFailedTestLogs()).isFalse();
    assertThat(config.testDurationThresholdMs()).isEqualTo(500.0);
    assertThat(config.stackFrameWhitelist()).containsExactlyElementsOf(whitelist);
    assertThat(config.stackFrameBlacklist()).containsExactlyElementsOf(blacklist);
  }

  @Test
  void builderUsesDefaults() {
    CompactorConfig config = DefaultCompactorConfig.builder().build();

    assertThat(config.enabled()).isEqualTo(CompactorConfig.DEFAULT_ENABLED);
    assertThat(config.outputPath()).isNull();
    assertThat(config.mode()).isNull();
    assertThat(config.outputAsJson()).isEqualTo(CompactorConfig.DEFAULT_OUTPUT_AS_JSON);
    assertThat(config.compressStackFrames())
        .isEqualTo(CompactorConfig.DEFAULT_COMPRESS_STACK_FRAMES);
    assertThat(config.showFixTargets()).isEqualTo(CompactorConfig.DEFAULT_SHOW_FIX_TARGETS);
    assertThat(config.showRecentChanges()).isEqualTo(CompactorConfig.DEFAULT_SHOW_RECENT_CHANGES);
    assertThat(config.showSlowTests()).isEqualTo(CompactorConfig.DEFAULT_SHOW_SLOW_TESTS);
    assertThat(config.showTotalDuration()).isEqualTo(CompactorConfig.DEFAULT_SHOW_TOTAL_DURATION);
    assertThat(config.showDurationReport()).isEqualTo(CompactorConfig.DEFAULT_SHOW_DURATION_REPORT);
    assertThat(config.showFailedTestLogs())
        .isEqualTo(CompactorConfig.DEFAULT_SHOW_FAILED_TEST_LOGS);
    assertThat(config.testDurationThresholdMs())
        .isEqualTo(CompactorConfig.DEFAULT_TEST_DURATION_THRESHOLD_MS);
    assertThat(config.stackFrameWhitelist()).isEmpty();
    assertThat(config.stackFrameBlacklist()).isEmpty();
  }

  @Test
  void listsAreUnmodifiable() {
    CompactorConfig config =
        DefaultCompactorConfig.builder()
            .stackFrameWhitelist(Collections.singletonList("a"))
            .build();

    List<String> whitelist = config.stackFrameWhitelist();
    assertThatThrownBy(() -> whitelist.add("b")).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void builderDefensiveCopiesLists() {
    List<String> original = new ArrayList<>(Collections.singletonList("a"));
    CompactorConfig config = DefaultCompactorConfig.builder().stackFrameWhitelist(original).build();

    original.add("b");
    assertThat(config.stackFrameWhitelist()).containsExactly("a");
  }

  @Test
  void nullListsBecomeEmpty() {
    CompactorConfig config =
        DefaultCompactorConfig.builder()
            .stackFrameWhitelist(null)
            .stackFrameBlacklist(null)
            .build();

    assertThat(config.stackFrameWhitelist()).isEmpty();
    assertThat(config.stackFrameBlacklist()).isEmpty();
  }

  @Test
  void resolvedConfigDelegatesAllMethods() {
    List<String> whitelist = Collections.singletonList("com.example");
    List<String> blacklist = Collections.singletonList("org.foo");

    CompactorConfig config =
        DefaultCompactorConfig.builder()
            .enabled(false)
            .outputPath("custom/path.json")
            .showRecentChanges(true)
            .showTotalDuration(true)
            .showDurationReport(true)
            .testDurationThresholdMs(500.0)
            .stackFrameWhitelist(whitelist)
            .stackFrameBlacklist(blacklist)
            .build();

    CompactorConfig resolved = config.resolved();

    assertThat(resolved.enabled()).isFalse();
    assertThat(resolved.outputPath()).isEqualTo("custom/path.json");
    assertThat(resolved.showRecentChanges()).isTrue();
    assertThat(resolved.showTotalDuration()).isTrue();
    assertThat(resolved.showDurationReport()).isTrue();
    assertThat(resolved.testDurationThresholdMs()).isEqualTo(500.0);
    assertThat(resolved.stackFrameWhitelist()).containsExactly("com.example");
    assertThat(resolved.stackFrameBlacklist()).containsExactly("org.foo");
  }

  @Test
  void mergeWhitelistMergesWithoutDuplicates() {
    List<String> userWhitelist = Arrays.asList("com.example", "org.base");
    List<List<String>> scans =
        Arrays.asList(
            Arrays.asList("org.base", "com.extra"), Arrays.asList("com.extra", "com.third"));

    List<String> merged = DefaultCompactorConfig.mergeWhitelist(userWhitelist, scans);

    assertThat(merged).containsExactly("com.example", "org.base", "com.extra", "com.third");
  }

  @Test
  void mergeWhitelistHandlesEmptyInputs() {
    assertThat(
            DefaultCompactorConfig.mergeWhitelist(Collections.emptyList(), Collections.emptyList()))
        .isEmpty();
    assertThat(
            DefaultCompactorConfig.mergeWhitelist(
                Collections.singletonList("a"), Collections.emptyList()))
        .containsExactly("a");
  }
}
