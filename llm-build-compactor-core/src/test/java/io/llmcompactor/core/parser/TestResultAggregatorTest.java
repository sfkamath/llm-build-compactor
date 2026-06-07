package io.llmcompactor.core.parser;

import static org.assertj.core.api.Assertions.assertThat;

import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.SlowTest;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class TestResultAggregatorTest {

  private static TestResult result(int testsRun, int failures, BuildError... errors) {
    return new TestResult(
        testsRun,
        failures,
        Arrays.asList(errors),
        Collections.emptyList(),
        Collections.emptyList());
  }

  @Test
  void emptyAggregatorHasZeroCounts() {
    TestResultAggregator agg = new TestResultAggregator();
    assertThat(agg.testsRun()).isEqualTo(0);
    assertThat(agg.failures()).isEqualTo(0);
    assertThat(agg.errors()).isEmpty();
    assertThat(agg.allDurations()).isEmpty();
    assertThat(agg.slowTests()).isEmpty();
  }

  @Test
  void addNullIsNoOp() {
    TestResultAggregator agg = new TestResultAggregator();
    agg.add(null);
    assertThat(agg.testsRun()).isEqualTo(0);
    assertThat(agg.errors()).isEmpty();
  }

  @Test
  void countsAccumulateAcrossMultipleResults() {
    TestResultAggregator agg = new TestResultAggregator();
    agg.add(result(10, 2));
    agg.add(result(5, 1));
    assertThat(agg.testsRun()).isEqualTo(15);
    assertThat(agg.failures()).isEqualTo(3);
  }

  @Test
  void errorsAccumulateAcrossMultipleResults() {
    BuildError e1 = new BuildError("TEST_FAILURE", "A.java", 1, "fail1", null);
    BuildError e2 = new BuildError("TEST_FAILURE", "B.java", 2, "fail2", null);
    TestResultAggregator agg = new TestResultAggregator();
    agg.add(result(1, 1, e1));
    agg.add(result(1, 1, e2));
    assertThat(agg.errors()).hasSize(2);
  }

  @Test
  void durationsAccumulate() {
    TestResult r1 =
        new TestResult(
            1, 0, Collections.emptyList(), Arrays.asList(100.0, 200.0), Collections.emptyList());
    TestResult r2 =
        new TestResult(
            1, 0, Collections.emptyList(), Arrays.asList(300.0), Collections.emptyList());
    TestResultAggregator agg = new TestResultAggregator();
    agg.add(r1);
    agg.add(r2);
    assertThat(agg.allDurations()).containsExactly(100.0, 200.0, 300.0);
  }

  @Test
  void slowTestsAccumulate() {
    SlowTest s1 = new SlowTest("A", "test1", 500.0);
    SlowTest s2 = new SlowTest("B", "test2", 600.0);
    TestResult r1 =
        new TestResult(
            1, 0, Collections.emptyList(), Collections.emptyList(), Collections.singletonList(s1));
    TestResult r2 =
        new TestResult(
            1, 0, Collections.emptyList(), Collections.emptyList(), Collections.singletonList(s2));
    TestResultAggregator agg = new TestResultAggregator();
    agg.add(r1);
    agg.add(r2);
    assertThat(agg.slowTests()).hasSize(2);
  }
}
