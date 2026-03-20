package io.llmcompactor.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleDetectorTest {

  @TempDir Path tempDir;

  @Test
  void shouldDetectMavenProject() throws IOException {
    Files.write(tempDir.resolve("pom.xml"), "<project/>".getBytes(), StandardOpenOption.CREATE);
    assertThat(ModuleDetector.isMaven(tempDir)).isTrue();
  }

  @Test
  void shouldDetectGradleProject() throws IOException {
    Files.write(
        tempDir.resolve("build.gradle"), "plugins {}".getBytes(), StandardOpenOption.CREATE);
    assertThat(ModuleDetector.isGradle(tempDir)).isTrue();

    Path gradleKts = tempDir.resolve("build.gradle.kts");
    Files.write(gradleKts, "plugins {}".getBytes(), StandardOpenOption.CREATE);
    assertThat(ModuleDetector.isGradle(tempDir)).isTrue();
  }

  @Test
  void shouldDetectModules() throws IOException {
    Path mod1 = tempDir.resolve("mod1");
    Files.createDirectories(mod1);
    Files.write(mod1.resolve("pom.xml"), "<project/>".getBytes(), StandardOpenOption.CREATE);

    Path mod2 = tempDir.resolve("mod2");
    Files.createDirectories(mod2);
    Files.write(mod2.resolve("pom.xml"), "<project/>".getBytes(), StandardOpenOption.CREATE);

    Path notAMod = tempDir.resolve("notAMod");
    Files.createDirectories(notAMod);

    List<String> modules = ModuleDetector.detectModules(tempDir);
    assertThat(modules).containsExactlyInAnyOrder("mod1", "mod2");
  }
}
