package io.llmcompactor.maven;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Field;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

class LlmInstallMojoTest {

  @Test
  void returnsEarlyWhenNotExecutionRoot() throws Exception {
    LlmInstallMojo mojo = new LlmInstallMojo();
    MavenProject project = new MavenProject();
    project.setExecutionRoot(false);
    setField(mojo, "project", project);
    assertThatCode(mojo::execute).doesNotThrowAnyException();
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
