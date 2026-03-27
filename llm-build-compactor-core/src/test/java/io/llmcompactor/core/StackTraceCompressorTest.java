package io.llmcompactor.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class StackTraceCompressorTest {

  @Test
  void shouldFilterNettyFrames() {
    String stackTrace =
        "java.lang.RuntimeException: Failed\n"
            + "at io.netty.channel.ChannelHandler.handle(ChannelHandler.java:100)\n"
            + "at io.netty.util.concurrent.EventExecutor.execute(EventExecutor.java:50)\n"
            + "at com.example.MyTest.testSomething(MyTest.java:30)\n"
            + "at org.junit.runner.JUnitCore.run(JUnitCore.java:10)";

    String compressed =
        StackTraceCompressor.compress(stackTrace, "com.example", Collections.emptyList());

    // Netty frames should be filtered
    assertThat(compressed).doesNotContain("io.netty");
    // Project frames should be kept
    assertThat(compressed).contains("com.example.MyTest");
    // JUnit frames should be filtered
    assertThat(compressed).doesNotContain("org.junit");
  }

  @Test
  void shouldFilterMicronautFrames() {
    String stackTrace =
        "io.micronaut.http.client.exceptions.HttpClientResponseException: Not Found\n"
            + "at io.micronaut.http.client.HttpClientFilter.doRequest(HttpClientFilter.java:52)\n"
            + "at io.netty.channel.SimpleChannelInboundHandler.channelRead(SimpleChannelInboundHandler.java:99)\n"
            + "at com.myapp.rest.ToolCallStatsControllerIT.testGetToolCallStats(ToolCallStatsControllerIT.java:166)";

    String compressed =
        StackTraceCompressor.compress(stackTrace, "com.myapp", Collections.emptyList());

    // Framework frames should be filtered
    assertThat(compressed).doesNotContain("io.micronaut");
    assertThat(compressed).doesNotContain("io.netty");
    // Project frames should be kept
    assertThat(compressed).contains("com.myapp.rest.ToolCallStatsControllerIT");
  }

  @Test
  void shouldKeepIncludedPackages() {
    String stackTrace =
        "java.lang.Exception: Error\n"
            + "at io.micronaut.http.HttpClient.get(HttpClient.java:25)\n"
            + "at com.myapp.service.OrderService.process(OrderService.java:42)\n"
            + "at com.myapp.test.OrderServiceTest.testProcess(OrderServiceTest.java:30)";

    // Explicitly include micronaut to keep those frames
    String compressed =
        StackTraceCompressor.compress(stackTrace, "com.myapp", Arrays.asList("io.micronaut"));

    // Micronaut frames should be kept due to explicit inclusion
    assertThat(compressed).contains("io.micronaut");
    // Project frames should still be kept
    assertThat(compressed).contains("com.myapp");
  }

  @Test
  void shouldPreserveCausedByLines() {
    String stackTrace =
        "java.lang.RuntimeException: Outer\n"
            + "at com.example.MyTest.test(MyTest.java:10)\n"
            + "Caused by: java.lang.IllegalArgumentException: Inner\n"
            + "at io.netty.handler.Codec.decode(Codec.java:50)\n"
            + "at com.example.Service.process(Service.java:25)";

    String compressed =
        StackTraceCompressor.compress(stackTrace, "com.example", Collections.emptyList());

    // Should preserve "Caused by" lines
    assertThat(compressed).contains("Caused by");
    // Should filter Netty
    assertThat(compressed).doesNotContain("io.netty");
    // Should keep project frames
    assertThat(compressed).contains("com.example");
  }

  @Test
  void shouldHandleEmptyStackTrace() {
    String compressed = StackTraceCompressor.compress("", "com.example", Collections.emptyList());
    assertThat(compressed).isEmpty();
  }

  @Test
  void shouldHandleNullStackTrace() {
    String compressed = StackTraceCompressor.compress(null, "com.example", Collections.emptyList());
    assertThat(compressed).isEmpty();
  }

  @Test
  void shouldKeepWhitelistedPackages() {
    String stackTrace =
        "java.lang.Exception: Error\n"
            + "at io.micronaut.http.HttpClient.get(HttpClient.java:25)\n"
            + "at com.myapp.service.OrderService.process(OrderService.java:42)\n"
            + "at com.myapp.test.OrderServiceTest.testProcess(OrderServiceTest.java:30)";

    String compressed =
        StackTraceCompressor.compress(
            stackTrace, "com.myapp", Arrays.asList("io.micronaut"), Collections.emptyList());

    assertThat(compressed).contains("io.micronaut");
    assertThat(compressed).contains("com.myapp");
  }

  @Test
  void shouldExcludeBlacklistedPackages() {
    String stackTrace =
        "java.lang.RuntimeException: Failed\n"
            + "at io.netty.channel.ChannelHandler.handle(ChannelHandler.java:100)\n"
            + "at com.myapp.service.OrderService.process(OrderService.java:42)\n"
            + "at com.myapp.test.OrderServiceTest.testProcess(OrderServiceTest.java:30)";

    String compressed =
        StackTraceCompressor.compress(
            stackTrace, "com.myapp", Collections.emptyList(), Arrays.asList("io.netty"));

    assertThat(compressed).doesNotContain("io.netty");
    assertThat(compressed).contains("com.myapp");
  }

  @Test
  void shouldAllowBlacklistToOverrideDefaults() {
    String stackTrace =
        "java.lang.RuntimeException: Failed\n"
            + "at org.springframework.web.servlet.DispatcherServlet.doGet(DispatcherServlet.java:100)\n"
            + "at com.myapp.controller.HomeController.index(HomeController.java:25)";

    String compressed =
        StackTraceCompressor.compress(
            stackTrace, "com.myapp", Collections.emptyList(), Arrays.asList("org.springframework"));

    assertThat(compressed).doesNotContain("org.springframework");
    assertThat(compressed).contains("com.myapp.controller.HomeController");
  }

  @Test
  void shouldAllowBlacklistToExcludeProjectPackages() {
    String stackTrace =
        "java.lang.RuntimeException: Failed\n"
            + "at com.myapp.service.InternalHelper.process(InternalHelper.java:50)\n"
            + "at com.myapp.controller.HomeController.index(HomeController.java:25)";

    String compressed =
        StackTraceCompressor.compress(
            stackTrace, "com.myapp", Collections.emptyList(), Arrays.asList("com.myapp.service"));

    assertThat(compressed).doesNotContain("InternalHelper");
    assertThat(compressed).contains("HomeController");
  }

  @Test
  void shouldGiveWhitelistPrecedenceOverBlacklist() {
    String stackTrace =
        "java.lang.Exception: Error\n"
            + "at io.micronaut.http.HttpClient.get(HttpClient.java:25)\n"
            + "at com.myapp.service.OrderService.process(OrderService.java:42)";

    String compressed =
        StackTraceCompressor.compress(
            stackTrace, "com.myapp", Arrays.asList("io.micronaut"), Arrays.asList("io.micronaut"));

    assertThat(compressed).contains("io.micronaut");
    assertThat(compressed).contains("com.myapp");
  }

  @Test
  void shouldHandleBothWhitelistAndBlacklist() {
    String stackTrace =
        "java.lang.Exception: Error\n"
            + "at io.micronaut.http.HttpClient.get(HttpClient.java:25)\n"
            + "at io.netty.channel.ChannelHandler.handle(ChannelHandler.java:100)\n"
            + "at org.springframework.web.servlet.DispatcherServlet.doGet(DispatcherServlet.java:100)\n"
            + "at com.myapp.controller.HomeController.index(HomeController.java:25)";

    String compressed =
        StackTraceCompressor.compress(
            stackTrace,
            "com.myapp",
            Arrays.asList("io.micronaut"),
            Arrays.asList("io.netty", "org.springframework"));

    assertThat(compressed).contains("io.micronaut");
    assertThat(compressed).doesNotContain("io.netty");
    assertThat(compressed).doesNotContain("org.springframework");
    assertThat(compressed).contains("com.myapp");
  }
}
