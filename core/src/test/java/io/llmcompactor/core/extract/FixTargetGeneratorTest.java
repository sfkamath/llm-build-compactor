package io.llmcompactor.core.extract;

import static org.assertj.core.api.Assertions.assertThat;

import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.FixTarget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FixTargetGeneratorTest {

  @TempDir Path tempDir;

  @Test
  void shouldGenerateFixTargetsFromErrors() throws IOException {
    Path file1 = tempDir.resolve("AppTest.java");
    Files.write(file1, "line 15 content".getBytes(), StandardOpenOption.CREATE);
    Path file2 = tempDir.resolve("App.java");
    Files.write(file2, "line 20 content".getBytes(), StandardOpenOption.CREATE);

    List<BuildError> errors =
        Arrays.asList(
            new BuildError("TestFailure", file1.toString(), 1, "Fail", "trace"),
            new BuildError("COMPILATION_ERROR", file2.toString(), 1, "Error", "trace"));

    List<FixTarget> targets = FixTargetGenerator.generate(errors);

    assertThat(targets).hasSize(2);
    assertThat(targets.get(0).file()).isEqualTo(file1.toString());
    assertThat(targets.get(1).file()).isEqualTo(file2.toString());
  }

  @Test
  void shouldDeDuplicateTargets() {
    List<BuildError> errors =
        Arrays.asList(
            new BuildError("TestFailure", "App.java", 10, "Fail1", "trace"),
            new BuildError("TestFailure", "App.java", 10, "Fail2", "trace"));

    List<FixTarget> targets = FixTargetGenerator.generate(errors);

    assertThat(targets).hasSize(1);
    assertThat(targets.get(0).reason()).isEqualTo("Fail1");
  }

  @Test
  void shouldSkipInvalidErrors() {
    List<BuildError> errors =
        Collections.singletonList(new BuildError("TestFailure", null, 0, "Fail", "trace"));

    List<FixTarget> targets = FixTargetGenerator.generate(errors);

    assertThat(targets).isEmpty();
  }
}
