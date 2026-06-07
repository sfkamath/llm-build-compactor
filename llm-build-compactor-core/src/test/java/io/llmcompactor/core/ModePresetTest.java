package io.llmcompactor.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModePresetTest {

  // -------------------------------------------------------------------------
  // from() factory
  // -------------------------------------------------------------------------

  @Test
  void fromNullReturnsNone() {
    assertThat(ModePreset.from(null)).isEqualTo(ModePreset.NONE);
  }

  @Test
  void fromEmptyStringReturnsNone() {
    assertThat(ModePreset.from("")).isEqualTo(ModePreset.NONE);
  }

  @Test
  void fromUnknownValueReturnsNone() {
    assertThat(ModePreset.from("turbo")).isEqualTo(ModePreset.NONE);
  }

  @Test
  void fromAgentCaseInsensitive() {
    assertThat(ModePreset.from("agent")).isEqualTo(ModePreset.AGENT);
    assertThat(ModePreset.from("AGENT")).isEqualTo(ModePreset.AGENT);
    assertThat(ModePreset.from("Agent")).isEqualTo(ModePreset.AGENT);
  }

  @Test
  void fromDebugCaseInsensitive() {
    assertThat(ModePreset.from("debug")).isEqualTo(ModePreset.DEBUG);
    assertThat(ModePreset.from("DEBUG")).isEqualTo(ModePreset.DEBUG);
  }

  @Test
  void fromHumanCaseInsensitive() {
    assertThat(ModePreset.from("human")).isEqualTo(ModePreset.HUMAN);
    assertThat(ModePreset.from("HUMAN")).isEqualTo(ModePreset.HUMAN);
  }

  // -------------------------------------------------------------------------
  // NONE — passes values through unchanged
  // -------------------------------------------------------------------------

  @Test
  void nonePassesThroughOutputAsJson() {
    assertThat(ModePreset.NONE.overrideOutputAsJson(true)).isTrue();
    assertThat(ModePreset.NONE.overrideOutputAsJson(false)).isFalse();
  }

  @Test
  void nonePassesThroughShowFixTargets() {
    assertThat(ModePreset.NONE.overrideShowFixTargets(true)).isTrue();
    assertThat(ModePreset.NONE.overrideShowFixTargets(false)).isFalse();
  }

  @Test
  void nonePassesThroughShowFailedTestLogs() {
    assertThat(ModePreset.NONE.overrideShowFailedTestLogs(true)).isTrue();
    assertThat(ModePreset.NONE.overrideShowFailedTestLogs(false)).isFalse();
  }

  // -------------------------------------------------------------------------
  // AGENT — JSON + fix targets, no logs
  // -------------------------------------------------------------------------

  @Test
  void agentForcesOutputAsJsonTrue() {
    assertThat(ModePreset.AGENT.overrideOutputAsJson(false)).isTrue();
    assertThat(ModePreset.AGENT.overrideOutputAsJson(true)).isTrue();
  }

  @Test
  void agentForcesShowFixTargetsTrue() {
    assertThat(ModePreset.AGENT.overrideShowFixTargets(false)).isTrue();
    assertThat(ModePreset.AGENT.overrideShowFixTargets(true)).isTrue();
  }

  @Test
  void agentForcesShowFailedTestLogsFalse() {
    assertThat(ModePreset.AGENT.overrideShowFailedTestLogs(true)).isFalse();
    assertThat(ModePreset.AGENT.overrideShowFailedTestLogs(false)).isFalse();
  }

  // -------------------------------------------------------------------------
  // DEBUG — JSON + fix targets + logs
  // -------------------------------------------------------------------------

  @Test
  void debugForcesOutputAsJsonTrue() {
    assertThat(ModePreset.DEBUG.overrideOutputAsJson(false)).isTrue();
  }

  @Test
  void debugForcesShowFixTargetsTrue() {
    assertThat(ModePreset.DEBUG.overrideShowFixTargets(false)).isTrue();
  }

  @Test
  void debugForcesShowFailedTestLogsTrue() {
    assertThat(ModePreset.DEBUG.overrideShowFailedTestLogs(false)).isTrue();
    assertThat(ModePreset.DEBUG.overrideShowFailedTestLogs(true)).isTrue();
  }

  // -------------------------------------------------------------------------
  // HUMAN — human-readable, no logs
  // -------------------------------------------------------------------------

  @Test
  void humanForcesOutputAsJsonFalse() {
    assertThat(ModePreset.HUMAN.overrideOutputAsJson(true)).isFalse();
    assertThat(ModePreset.HUMAN.overrideOutputAsJson(false)).isFalse();
  }

  @Test
  void humanForcesShowFixTargetsTrue() {
    assertThat(ModePreset.HUMAN.overrideShowFixTargets(false)).isTrue();
  }

  @Test
  void humanForcesShowFailedTestLogsFalse() {
    assertThat(ModePreset.HUMAN.overrideShowFailedTestLogs(true)).isFalse();
    assertThat(ModePreset.HUMAN.overrideShowFailedTestLogs(false)).isFalse();
  }
}
