package io.llmcompactor.core.context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ModuleDetector {

  public static boolean isMaven(Path root) {
    return Files.exists(root.resolve("pom.xml"));
  }

  public static boolean isGradle(Path root) {
    return Files.exists(root.resolve("build.gradle"))
        || Files.exists(root.resolve("build.gradle.kts"));
  }

  public static List<String> detectModules(Path root) {

    List<String> modules = new ArrayList<>();

    try {

      Files.list(root)
          .filter(
              p ->
                  Files.exists(p.resolve("pom.xml"))
                      || Files.exists(p.resolve("build.gradle"))
                      || Files.exists(p.resolve("build.gradle.kts")))
          .forEach(p -> modules.add(p.getFileName().toString()));

    } catch (Exception ignored) {
    }

    return modules;
  }

  private ModuleDetector() {}
}
