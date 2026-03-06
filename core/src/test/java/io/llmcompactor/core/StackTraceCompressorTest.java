package io.llmcompactor.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StackTraceCompressorTest {

  private static final String PROJECT_PKG = "io.llmcompactor.";

  @Test
  void shouldKeepProjectFrames() {
    String trace =
        "at io.llmcompactor.core.StackTraceCompressor.compress(StackTraceCompressor.java:20)\n"
            + "at io.llmcompactor.core.SummaryWriter.write(SummaryWriter.java:15)\n"
            + "at org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:10)";

    String compressed = StackTraceCompressor.compress(trace, PROJECT_PKG, Collections.emptyList());

    assertThat(compressed)
        .contains("io.llmcompactor.core.StackTraceCompressor.compress")
        .contains("io.llmcompactor.core.SummaryWriter.write")
        .doesNotContain("org.junit.jupiter.api.AssertionUtils.fail");
  }

  @Test
  void shouldHandleCausalChains() {
    String trace =
        "java.lang.RuntimeException: Wrapper\n"
            + "at io.llmcompactor.App.run(App.java:10)\n"
            + "Caused by: java.lang.IllegalArgumentException: Root\n"
            + "at io.llmcompactor.Service.doWork(Service.java:20)\n"
            + "at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:10)";

    String compressed = StackTraceCompressor.compress(trace, PROJECT_PKG, Collections.emptyList());

    // Note: Currently 'RuntimeException: Wrapper' is not preserved because it doesn't start with
    // 'at ' or 'Caused by:'
    // Wait, let's look at the implementation again.

    assertThat(compressed)
        .contains("at io.llmcompactor.App.run")
        .contains("Caused by: java.lang.IllegalArgumentException: Root")
        .contains("at io.llmcompactor.Service.doWork")
        .doesNotContain("org.springframework.web.servlet.DispatcherServlet");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "java.base/java.lang.Thread.run",
        "javax.servlet.http.HttpServlet.service",
        "org.junit.jupiter.engine.execution.MethodInvocation.proceed",
        "io.projectreactor.core.publisher.Flux.subscribe",
        "io.micronaut.http.server.netty.HttpContentSource.onData"
      })
  void shouldFilterFrameworkFrames(String frame) {
    String trace =
        "at " + frame + "(File.java:10)\n" + "at io.llmcompactor.MyCode.call(MyCode.java:5)";

    String compressed =
        StackTraceCompressor.compress(trace, "io.llmcompactor.", Collections.emptyList());

    assertThat(compressed).contains("io.llmcompactor.MyCode.call").doesNotContain(frame);
  }

  @Test
  void shouldRespectIncludePackages() {
    String trace =
        "at io.llmcompactor.MyCode.call(MyCode.java:5)\n"
            + "at org.junit.jupiter.api.Assertions.fail(Assertions.java:10)";

    // When junit is explicitly included
    String compressed = StackTraceCompressor.compress(trace, PROJECT_PKG, List.of("org.junit."));

    assertThat(compressed)
        .contains("io.llmcompactor.MyCode.call")
        .contains("org.junit.jupiter.api.Assertions.fail");
  }

  @Test
  void shouldReturnEmptyStringIfNoUsefulFramesFound() {
    // Current implementation returns empty string if no frames match.
    // Wait, let me check the implementation again.
    String trace =
        "at org.junit.jupiter.api.Assertions.fail(Assertions.java:10)\n"
            + "at org.junit.jupiter.api.AssertTrue.assertTrue(AssertTrue.java:32)";

    String compressed = StackTraceCompressor.compress(trace, PROJECT_PKG, Collections.emptyList());

    assertThat(compressed).isEmpty();
  }

  @Test
  void shouldHandleNullOrEmpty() {
    assertThat(StackTraceCompressor.compress(null, "io.", Collections.emptyList())).isEmpty();
    assertThat(StackTraceCompressor.compress("", "io.", Collections.emptyList())).isEmpty();
  }

  @Test
  void shouldHandleFramesWithoutAtPrefix() {
    String trace = "Non-frame line\nat io.llmcompactor.App.main(App.java:5)";
    String compressed = StackTraceCompressor.compress(trace, PROJECT_PKG, Collections.emptyList());
    assertThat(compressed).isEqualTo("at io.llmcompactor.App.main(App.java:5)");
  }
}
