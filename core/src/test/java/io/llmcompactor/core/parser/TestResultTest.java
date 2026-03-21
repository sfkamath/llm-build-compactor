package io.llmcompactor.core.parser;

import static org.assertj.core.api.Assertions.assertThat;

import io.llmcompactor.core.BuildError;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestResultTest {

  @Test
  void shouldExposeFieldsViaJacksonGetters() {
    List<BuildError> errors =
        Collections.singletonList(new BuildError("Type", "File.java", 1, "msg", "stack"));
    List<Double> durations = Arrays.asList(10.0, 20.0);

    TestResult result = new TestResult(5, 1, errors, durations);

    assertThat(result.getTestsRun()).isEqualTo(5);
    assertThat(result.getFailures()).isEqualTo(1);
    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getAllDurations()).containsExactly(10.0, 20.0);
  }

  @Test
  void shouldDefaultToEmptyDurationsViaThreeArgConstructor() {
    TestResult result = new TestResult(2, 0, Collections.emptyList());

    assertThat(result.allDurations()).isEmpty();
  }

  @Test
  void shouldHandleNullListsAsEmpty() {
    TestResult result = new TestResult(0, 0, null, null);

    assertThat(result.errors()).isEmpty();
    assertThat(result.allDurations()).isEmpty();
  }

  @Test
  void shouldImplementEqualsAndHashCode() {
    TestResult a = new TestResult(3, 1, Collections.emptyList());
    TestResult b = new TestResult(3, 1, Collections.emptyList());
    TestResult c = new TestResult(5, 0, Collections.emptyList());

    assertThat(a).isEqualTo(b);
    assertThat(a).isNotEqualTo(c);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a).isNotEqualTo(null);
    assertThat(a).isEqualTo(a);
  }

  @Test
  void shouldImplementToString() {
    TestResult result = new TestResult(3, 1, Collections.emptyList());

    assertThat(result.toString()).contains("3").contains("1");
  }
}
