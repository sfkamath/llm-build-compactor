package io.llmcompactor.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BuildSummaryTest {

  @Test
  void shouldDeduplicateErrors() {
    BuildError error1 = new BuildError("type1", "file1", 10, "msg1", "stack1");
    BuildError error2 = new BuildError("type1", "file1", 10, "msg1", "stack1"); // Duplicate of 1
    BuildError error3 = new BuildError("type2", "file2", 20, "msg2", "stack2");

    BuildSummary summary =
        new BuildSummary("FAILED", 10, 2, List.of(error1, error2, error3), null, null);

    assertThat(summary.errors()).hasSize(2);
    assertThat(summary.errors()).containsExactly(error1, error3);
  }
}
