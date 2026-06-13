package io.llmcompactor.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import io.llmcompactor.core.CompactorConfig;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LlmCompactorPluginDefaultsTest {

  @TempDir Path tempDir;
  @TempDir Path testKitDir;

  @Test
  void conventionDefaultsMatchCompactorDefaults() {
    System.setProperty("llmce", "true");
    try {
      Project project = ProjectBuilder.builder().withGradleUserHomeDir(tempDir.toFile()).build();
      project.getPluginManager().apply(LlmCompactorPlugin.class);
      LlmCompactorPlugin.LlmCompactorExtension ext =
          project.getExtensions().getByType(LlmCompactorPlugin.LlmCompactorExtension.class);

      assertThat(ext.getOutputAsJson().get()).isEqualTo(CompactorConfig.DEFAULT_OUTPUT_AS_JSON);
      assertThat(ext.getCompressStackFrames().get())
          .isEqualTo(CompactorConfig.DEFAULT_COMPRESS_STACK_FRAMES);
      assertThat(ext.getShowFixTargets().get()).isEqualTo(CompactorConfig.DEFAULT_SHOW_FIX_TARGETS);
      assertThat(ext.getShowRecentChanges().get())
          .isEqualTo(CompactorConfig.DEFAULT_SHOW_RECENT_CHANGES);
      assertThat(ext.getShowSlowTests().get()).isEqualTo(CompactorConfig.DEFAULT_SHOW_SLOW_TESTS);
      assertThat(ext.getShowTotalDuration().get())
          .isEqualTo(CompactorConfig.DEFAULT_SHOW_TOTAL_DURATION);
      assertThat(ext.getShowDurationReport().get())
          .isEqualTo(CompactorConfig.DEFAULT_SHOW_DURATION_REPORT);
      assertThat(ext.getShowFailedTestLogs().get())
          .isEqualTo(CompactorConfig.DEFAULT_SHOW_FAILED_TEST_LOGS);
      assertThat(ext.getTestDurationThresholdMs().get())
          .isEqualTo(CompactorConfig.DEFAULT_TEST_DURATION_THRESHOLD_MS);
    } finally {
      System.clearProperty("llmce");
    }
  }

  @Test
  void testCountLoggerLineNotInOutput() throws Exception {
    URL resource = getClass().getClassLoader().getResource("test-project");
    Path projectDir = Paths.get(resource.toURI());

    BuildResult result =
        GradleRunner.create()
            .withTestKitDir(testKitDir.toFile())
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("test", "-PenableCompactor", "--console=plain", "--info")
            .buildAndFail();

    for (String line : result.getOutput().split("\n")) {
      assertThat(line.trim())
          .as("Unexpected test count line: %s", line)
          .doesNotMatch("\\d+ tests? completed.*");
    }
  }

  @Test
  void javaCompileTasksHaveLintSuppressingArgs() {
    PrintStream savedOut = System.out;
    PrintStream savedErr = System.err;
    try {
      Project project = ProjectBuilder.builder().withGradleUserHomeDir(tempDir.toFile()).build();
      project.getPluginManager().apply("java");
      project.getPluginManager().apply(LlmCompactorPlugin.class);

      JavaCompile compileJava =
          project.getTasks().withType(JavaCompile.class).getByName("compileJava");

      assertThat(compileJava.getOptions().isWarnings()).isFalse();
      assertThat(compileJava.getOptions().isDeprecation()).isFalse();
      assertThat(compileJava.getOptions().getCompilerArgs())
          .contains(
              "-nowarn",
              "-Xlint:none",
              "-Xlint:-processing",
              "-Xlint:-unchecked",
              "-Xlint:-deprecation",
              "-Xlint:-options");
    } finally {
      System.setOut(savedOut);
      System.setErr(savedErr);
    }
  }
}
