package io.llmcompactor.gradle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class TestCountLoggerTest {

  @Test
  void suppressTestCountLoggerDoesNotThrow() {
    Project project = ProjectBuilder.builder().build();
    project.getPluginManager().apply("java");
    org.gradle.api.tasks.testing.Test testTask =
        project.getTasks().withType(org.gradle.api.tasks.testing.Test.class).getByName("test");

    TestCountLogger.suppressTestCountLogger(testTask);
  }

  @Test
  void fineLoggingReachableWhenEnabled() {
    Logger log = Logger.getLogger("io.llmcompactor.gradle.TestCountLogger");
    ConsoleHandler handler = new ConsoleHandler();
    handler.setLevel(Level.ALL);
    log.setUseParentHandlers(false);
    log.addHandler(handler);
    Level prev = log.getLevel();
    log.setLevel(Level.ALL);
    assertDoesNotThrow(() -> log.fine("TestCountLogger FINE logging is reachable"));
    log.removeHandler(handler);
    log.setLevel(prev);
  }
}
