package io.llmcompactor.extension;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PropertyResolverTest {

  private static final String KEY = "llmCompactor.testProp";

  @AfterEach
  void clearSysProp() {
    System.clearProperty(KEY);
  }

  @Test
  void defaultReturnedWhenNoSource() {
    PropertyResolver resolver = new PropertyResolver(null, null);
    assertThat(resolver.getString(KEY, "fallback")).isEqualTo("fallback");
  }

  @Test
  void sysPropTakesPrecedenceOverProjectProps() {
    System.setProperty(KEY, "fromSys");
    Properties proj = new Properties();
    proj.setProperty(KEY, "fromProj");
    PropertyResolver resolver = new PropertyResolver(null, proj);
    assertThat(resolver.getString(KEY, "default")).isEqualTo("fromSys");
  }

  @Test
  void projectPropUsedWhenNoSysProp() {
    Properties proj = new Properties();
    proj.setProperty(KEY, "fromProj");
    PropertyResolver resolver = new PropertyResolver(null, proj);
    assertThat(resolver.getString(KEY, "default")).isEqualTo("fromProj");
  }

  @Test
  void getBooleanParsesTrue() {
    Properties proj = new Properties();
    proj.setProperty(KEY, "true");
    assertThat(new PropertyResolver(null, proj).getBoolean(KEY, false)).isTrue();
  }

  @Test
  void getBooleanParsesFalse() {
    Properties proj = new Properties();
    proj.setProperty(KEY, "false");
    assertThat(new PropertyResolver(null, proj).getBoolean(KEY, true)).isFalse();
  }

  @Test
  void getBooleanCaseInsensitive() {
    Properties proj = new Properties();
    proj.setProperty(KEY, "TRUE");
    assertThat(new PropertyResolver(null, proj).getBoolean(KEY, false)).isTrue();
  }

  @Test
  void getDoubleReturnsDefault() {
    assertThat(new PropertyResolver(null, null).getDouble(KEY, 42.5)).isEqualTo(42.5);
  }

  @Test
  void getDoubleParsesProp() {
    Properties proj = new Properties();
    proj.setProperty(KEY, "3.14");
    assertThat(new PropertyResolver(null, proj).getDouble(KEY, 0.0)).isEqualTo(3.14);
  }

  @Test
  void getDoubleInvalidValueFallsToDefault() {
    Properties proj = new Properties();
    proj.setProperty(KEY, "notanumber");
    assertThat(new PropertyResolver(null, proj).getDouble(KEY, 99.0)).isEqualTo(99.0);
  }

  @Test
  void nullProjectPropsHandledGracefully() {
    PropertyResolver resolver = new PropertyResolver(null, null);
    assertThat(resolver.getBoolean(KEY, true)).isTrue();
  }
}
