package io.llmcompactor.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.Project;

final class GradlePropertiesInstaller {

  private static final String MARKER_START = "# >>> llm-compactor >>>";
  private static final String MARKER_END = "# <<< llm-compactor <<<";

  private GradlePropertiesInstaller() {}

  static void registerTasks(Project project) {
    project
        .getTasks()
        .register(
            "installLlmCompactor",
            task -> {
              task.setGroup("llm-compactor");
              task.setDescription(
                  "Adds org.gradle.logging.level=quiet to the root project's gradle.properties");
              task.doLast(
                  t -> {
                    try {
                      Path rootProjectDir =
                          t.getProject().getRootProject().getProjectDir().toPath();
                      install(rootProjectDir);
                      t.getProject()
                          .getLogger()
                          .quiet(
                              "[LLM Compactor] Set org.gradle.logging.level=quiet in {}."
                                  + " To remove it: ./gradlew uninstallLlmCompactor",
                              rootProjectDir.resolve("gradle.properties"));
                    } catch (IOException e) {
                      throw new RuntimeException("Failed to install LLM Compactor", e);
                    }
                  });
            });

    project
        .getTasks()
        .register(
            "uninstallLlmCompactor",
            task -> {
              task.setGroup("llm-compactor");
              task.setDescription(
                  "Removes the org.gradle.logging.level=quiet entry from the root project's gradle.properties");
              task.doLast(
                  t -> {
                    Path rootProjectDir = t.getProject().getRootProject().getProjectDir().toPath();
                    try {
                      boolean removed = uninstall(rootProjectDir);
                      t.getProject()
                          .getLogger()
                          .quiet(
                              removed
                                  ? "[LLM Compactor] Removed gradle.properties entry."
                                  : "[LLM Compactor] Nothing to remove (gradle.properties entry not found).");
                    } catch (IOException e) {
                      throw new RuntimeException("Failed to uninstall LLM Compactor", e);
                    }
                  });
            });
  }

  static void autoInstall(Project project) {
    try {
      install(project.getRootProject().getProjectDir().toPath());
    } catch (IOException e) {
      project.getLogger().warn("[LLM Compactor] Could not install: {}", e.getMessage());
    }
  }

  static void install(Path projectDir) throws IOException {
    Path propsFile = projectDir.resolve("gradle.properties");
    String content =
        Files.exists(propsFile) ? new String(Files.readAllBytes(propsFile), "UTF-8") : "";
    if (content.contains(MARKER_START)) {
      return;
    }
    String block =
        "\n" + MARKER_START + "\n" + "org.gradle.logging.level=quiet\n" + MARKER_END + "\n";
    if (!content.isEmpty() && !content.endsWith("\n")) {
      content += "\n";
    }
    Files.write(propsFile, (content + block).getBytes("UTF-8"));
  }

  static boolean uninstall(Path projectDir) throws IOException {
    Path propsFile = projectDir.resolve("gradle.properties");
    if (!Files.exists(propsFile)) {
      return false;
    }
    String content = new String(Files.readAllBytes(propsFile), "UTF-8");
    int start = content.indexOf(MARKER_START);
    if (start < 0) {
      return false;
    }
    int end = content.indexOf(MARKER_END, start);
    if (end < 0) {
      return false;
    }
    end += MARKER_END.length();
    if (end < content.length() && content.charAt(end) == '\n') {
      end++;
    }
    if (start > 0 && content.charAt(start - 1) == '\n') {
      start--;
    }
    Files.write(
        propsFile, (content.substring(0, start) + content.substring(end)).getBytes("UTF-8"));
    return true;
  }
}
