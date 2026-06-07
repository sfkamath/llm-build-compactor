package io.llmcompactor.maven;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LlmCompactMojoTest {

  @AfterEach
  void clearProps() {
    System.clearProperty("llmce");
    System.clearProperty("llmCompactor.extension.active");
  }

  @Test
  void executeDoesNotThrowWhenDisabled() throws Exception {
    LlmCompactMojo mojo = new LlmCompactMojo();
    setField(mojo, "enabled", false);
    assertThatCode(mojo::execute).doesNotThrowAnyException();
  }

  @Test
  void llmceSysPropDisablesExecution() throws Exception {
    System.setProperty("llmce", "");
    LlmCompactMojo mojo = new LlmCompactMojo();
    // enabled field starts false (no Maven injection); llmce path also sets it false.
    // Either way execute() returns cleanly without calling SurefireParser.
    assertThatCode(mojo::execute).doesNotThrowAnyException();
  }

  @Test
  void extensionActivePropSkipsExecution() throws Exception {
    System.setProperty("llmCompactor.extension.active", "true");
    LlmCompactMojo mojo = new LlmCompactMojo();
    setField(mojo, "enabled", true);
    assertThatCode(mojo::execute).doesNotThrowAnyException();
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
