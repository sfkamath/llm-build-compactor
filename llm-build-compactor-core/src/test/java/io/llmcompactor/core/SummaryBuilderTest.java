package io.llmcompactor.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SummaryBuilderTest {

  private static BuildError error(String type, String file, int line, String msg) {
    return new BuildError(type, file, line, msg, null);
  }

  @Test
  void emptyBuilderProducesSuccess() {
    BuildSummary summary = new SummaryBuilder().build();
    assertThat(summary.status()).isEqualTo("SUCCESS");
    assertThat(summary.testsRun()).isEqualTo(0);
    assertThat(summary.failures()).isEqualTo(0);
    assertThat(summary.errors()).isEmpty();
  }

  @Test
  void buildFailedFlagFlipsStatusToFailed() {
    BuildSummary summary = new SummaryBuilder().withBuildFailed(true).build();
    assertThat(summary.status()).isEqualTo("FAILED");
  }

  @Test
  void errorsFlipStatusToFailed() {
    BuildSummary summary =
        new SummaryBuilder()
            .addErrors(Collections.singletonList(error("TEST_FAILURE", "Foo.java", 1, "msg")))
            .build();
    assertThat(summary.status()).isEqualTo("FAILED");
  }

  @Test
  void testsRunAndFailuresAccumulate() {
    BuildSummary summary =
        new SummaryBuilder()
            .withTestsRun(5)
            .withTestsRun(3)
            .withFailures(1)
            .withFailures(2)
            .build();
    assertThat(summary.testsRun()).isEqualTo(8);
    assertThat(summary.failures()).isEqualTo(3);
  }

  @Test
  void withBuildFailedFalseDoesNotOverridePreviousTrue() {
    BuildSummary summary =
        new SummaryBuilder().withBuildFailed(true).withBuildFailed(false).build();
    assertThat(summary.status()).isEqualTo("FAILED");
  }

  @Test
  void showFixTargetsFalseProducesEmptyTargets() {
    BuildError e = error("TEST_FAILURE", "src/Foo.java", 10, "assertion failed");
    BuildSummary summary =
        new SummaryBuilder()
            .addErrors(Collections.singletonList(e))
            .withShowFixTargets(false)
            .build();
    assertThat(summary.fixTargets()).isEmpty();
  }

  @Test
  void showSlowTestsFalseProducesEmptySlowTests() {
    SlowTest slow = new SlowTest("FooTest", "testFoo", 500.0);
    BuildSummary summary =
        new SummaryBuilder()
            .addSlowTests(Collections.singletonList(slow))
            .withShowSlowTests(false)
            .build();
    assertThat(summary.slowTests()).isEmpty();
  }

  @Test
  void slowTestsFilteredByThreshold() {
    List<SlowTest> tests =
        Arrays.asList(new SlowTest("T", "fast", 50.0), new SlowTest("T", "slow", 500.0));
    BuildSummary summary =
        new SummaryBuilder()
            .addSlowTests(tests)
            .withShowSlowTests(true)
            .withTestDurationThresholdMs(200.0)
            .build();
    assertThat(summary.slowTests()).hasSize(1);
    assertThat(summary.slowTests().get(0).testName()).isEqualTo("slow");
  }

  @Test
  void showDurationReportFalseProducesNullPercentiles() {
    BuildSummary summary =
        new SummaryBuilder()
            .addDurations(Arrays.asList(100.0, 200.0, 300.0))
            .withShowDurationReport(false)
            .build();
    assertThat(summary.testDurationPercentiles()).isNull();
  }

  @Test
  void showDurationReportTrueProducesPercentiles() {
    BuildSummary summary =
        new SummaryBuilder()
            .addDurations(Arrays.asList(100.0, 200.0, 300.0, 400.0, 500.0))
            .withShowDurationReport(true)
            .build();
    assertThat(summary.testDurationPercentiles()).isNotNull();
    assertThat(summary.testDurationPercentiles()).containsKey("p50");
    assertThat(summary.testDurationPercentiles()).containsKey("max");
  }

  @Test
  void showRecentChangesFalseProducesEmptyList() {
    BuildSummary summary = new SummaryBuilder().withShowRecentChanges(false).build();
    assertThat(summary.recentChanges()).isEmpty();
  }

  @Test
  void showTotalDurationFalseProducesNullDuration() {
    BuildSummary summary =
        new SummaryBuilder().withShowTotalDuration(false).withSessionStartTime(1000L).build();
    assertThat(summary.totalBuildDurationMs()).isNull();
  }

  @Test
  void showTotalDurationTrueProducesNonNullDuration() {
    long start = System.currentTimeMillis() - 5000L;
    BuildSummary summary =
        new SummaryBuilder().withShowTotalDuration(true).withSessionStartTime(start).build();
    assertThat(summary.totalBuildDurationMs()).isNotNull();
    assertThat(summary.totalBuildDurationMs()).isGreaterThan(0L);
  }

  @Test
  void addNullErrorsIsNoOp() {
    BuildSummary summary = new SummaryBuilder().addErrors(null).build();
    assertThat(summary.errors()).isEmpty();
    assertThat(summary.status()).isEqualTo("SUCCESS");
  }

  @Test
  void multipleAddErrorsCallsAccumulate() {
    BuildError e1 = error("TEST_FAILURE", "A.java", 1, "fail1");
    BuildError e2 = error("TEST_FAILURE", "B.java", 2, "fail2");
    BuildSummary summary =
        new SummaryBuilder()
            .addErrors(Collections.singletonList(e1))
            .addErrors(Collections.singletonList(e2))
            .build();
    assertThat(summary.errors()).hasSize(2);
  }
}
