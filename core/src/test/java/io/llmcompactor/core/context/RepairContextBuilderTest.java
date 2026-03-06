package io.llmcompactor.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import io.llmcompactor.core.BuildSummary;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepairContextBuilderTest {

  @TempDir Path tempDir;

  @Test
  void shouldBuildRepairContext() throws IOException {
    BuildSummary summary =
        new BuildSummary(
            "FAILED",
            1,
            1,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());

    Map<String, Object> context = RepairContextBuilder.build(summary);

    assertThat(context.get("status")).isEqualTo("FAILED");
  }
}
