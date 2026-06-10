package io.llmcompactor.gradle;

import static io.llmcompactor.gradle.TestCountLogger.suppressTestCountLogger;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.logging.TestLogEvent;

final class BuildOutputSuppressor {

  private BuildOutputSuppressor() {}

  static void apply(Project rootProject, boolean isEnabled) {
    if (isEnabled) {
      System.setProperty("org.gradle.logging.level", "quiet");
      rootProject
          .getGradle()
          .getStartParameter()
          .setLogLevel(org.gradle.api.logging.LogLevel.ERROR);
      rootProject
          .getGradle()
          .getStartParameter()
          .setWarningMode(org.gradle.api.logging.configuration.WarningMode.None);
      rootProject
          .getGradle()
          .getStartParameter()
          .setShowStacktrace(
              org.gradle.api.logging.configuration.ShowStacktrace.INTERNAL_EXCEPTIONS);
      PrintStream nullPrint = CompletionService.nullStream();
      System.setOut(nullPrint);
      System.setErr(nullPrint);

      rootProject.allprojects(
          p -> {
            p.getLogging().captureStandardOutput(org.gradle.api.logging.LogLevel.DEBUG);
            p.getLogging().captureStandardError(org.gradle.api.logging.LogLevel.DEBUG);
          });
    } else {
      // If compactor is disabled, but quiet mode is active (likely due to our auto-install),
      // we should restore LIFECYCLE so failures are visible.
      LogLevel currentLevel = rootProject.getGradle().getStartParameter().getLogLevel();
      String propLevel = System.getProperty("org.gradle.logging.level");
      if ((currentLevel == LogLevel.QUIET || "quiet".equalsIgnoreCase(propLevel))
          && isQuietSetByPlugin(rootProject)) {
        rootProject.getGradle().getStartParameter().setLogLevel(LogLevel.LIFECYCLE);
        System.clearProperty("org.gradle.logging.level");
      }
    }

    rootProject.allprojects(
        p -> {
          p.getTasks()
              .configureEach(
                  task -> {
                    if (isEnabled) {
                      applyQuietTaskLogging(task);
                    }
                  });
          p.getTasks()
              .withType(JavaCompile.class)
              .configureEach(
                  task -> {
                    if (isEnabled) {
                      applyQuietJavaCompileOptions(task);
                    }
                  });
          p.getTasks()
              .withType(Checkstyle.class)
              .configureEach(
                  task -> {
                    if (isEnabled) {
                      task.setShowViolations(false);
                    }
                  });
          p.getTasks()
              .withType(Test.class)
              .configureEach(
                  task -> {
                    if (isEnabled) {
                      task.systemProperty("slf4j.internal.verbosity", "ERROR");
                      task.getTestLogging().setEvents(EnumSet.noneOf(TestLogEvent.class));
                      task.getTestLogging().setShowStandardStreams(false);
                      task.getTestLogging().setShowExceptions(false);
                      task.getTestLogging().setShowCauses(false);
                      task.getTestLogging().setShowStackTraces(false);

                      task.addTestOutputListener(
                          (descriptor, event) -> {
                            // Swallow test output from build log; XML results capture it
                          });
                      suppressTestCountLogger(task);
                    } else {
                      // If we restored LIFECYCLE, ensure test failures are actually shown
                      if (rootProject.getGradle().getStartParameter().getLogLevel()
                              == LogLevel.LIFECYCLE
                          && isQuietSetByPlugin(rootProject)) {
                        task.getTestLogging().getQuiet().getEvents().add(TestLogEvent.FAILED);
                        task.getTestLogging().getQuiet().setShowExceptions(true);
                        task.getTestLogging().getQuiet().setShowCauses(true);
                      }
                    }
                  });
          p.getTasks()
              .withType(JavaExec.class)
              .configureEach(
                  task -> {
                    if (isEnabled) {
                      task.systemProperty("slf4j.internal.verbosity", "ERROR");
                    }
                  });
        });
  }

  private static void applyQuietTaskLogging(Task task) {
    task.getLogging().captureStandardOutput(LogLevel.DEBUG);
    task.getLogging().captureStandardError(LogLevel.DEBUG);
  }

  private static void applyQuietJavaCompileOptions(JavaCompile task) {
    task.getOptions().setWarnings(false);
    task.getOptions().setDeprecation(false);
    // Groovy DSL may populate compilerArgs with GStringImpl; normalize to plain Strings
    // to avoid ClassCastException when iterating a List<String> that contains GString values.
    @SuppressWarnings("unchecked")
    List<Object> rawArgs = (List<Object>) (List<?>) task.getOptions().getCompilerArgs();
    List<String> compilerArgs = new ArrayList<>();
    for (Object arg : rawArgs) {
      compilerArgs.add(arg.toString());
    }
    compilerArgs.removeIf("-Amicronaut.processing.incremental=true"::equals);
    if (!containsCompilerArg(compilerArgs, "-nowarn")) {
      compilerArgs.add("-nowarn");
    }
    if (!containsCompilerArg(compilerArgs, "-Xlint:none")) {
      compilerArgs.add("-Xlint:none");
    }
    if (!containsCompilerArg(compilerArgs, "-Xlint:-processing")) {
      compilerArgs.add("-Xlint:-processing");
    }
    if (!containsCompilerArg(compilerArgs, "-Xlint:-unchecked")) {
      compilerArgs.add("-Xlint:-unchecked");
    }
    if (!containsCompilerArg(compilerArgs, "-Xlint:-deprecation")) {
      compilerArgs.add("-Xlint:-deprecation");
    }
    if (!containsCompilerArg(compilerArgs, "-Xlint:-options")) {
      compilerArgs.add("-Xlint:-options");
    }
    // -Xlint:-removal is Java 11+ only, skip for Java 8 compatibility
    task.getOptions().setCompilerArgs(compilerArgs);
  }

  private static boolean containsCompilerArg(List<String> compilerArgs, String value) {
    for (String arg : compilerArgs) {
      if (value.equals(arg)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isQuietSetByPlugin(Project project) {
    Path propsFile = project.getProjectDir().toPath().resolve("gradle.properties");
    if (!Files.exists(propsFile)) {
      return false;
    }
    try {
      byte[] bytes = Files.readAllBytes(propsFile);
      String content = new String(bytes, StandardCharsets.UTF_8);
      return content.contains(GradlePropertiesInstaller.MARKER_START);
    } catch (IOException e) {
      project.getLogger().debug("Could not read gradle.properties: " + e.getMessage());
      return false;
    }
  }
}
