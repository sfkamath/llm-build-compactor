package io.llmcompactor.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlowTestTest {

  @Test
  void shouldGetValues() {
    SlowTest slowTest = new SlowTest("com.example.Test", "testMethod", 1500.5);

    assertThat(slowTest.className()).isEqualTo("com.example.Test");
    assertThat(slowTest.testName()).isEqualTo("testMethod");
    assertThat(slowTest.testDuration()).isEqualTo(1500.5);

    assertThat(slowTest.className()).isEqualTo("com.example.Test");
    assertThat(slowTest.testName()).isEqualTo("testMethod");
    assertThat(slowTest.testDuration()).isEqualTo(1500.5);
  }

  @Test
  void shouldImplementEqualsAndHashCode() {
    SlowTest test1 = new SlowTest("ClassA", "Method1", 100.0);
    SlowTest test2 = new SlowTest("ClassA", "Method1", 100.0);
    SlowTest test3 = new SlowTest("ClassB", "Method1", 100.0);
    SlowTest test4 = new SlowTest("ClassA", "Method2", 100.0);
    SlowTest test5 = new SlowTest("ClassA", "Method1", 200.0);

    assertThat(test1).isEqualTo(test1);
    assertThat(test1).isEqualTo(test2);
    assertThat(test1.hashCode()).isEqualTo(test2.hashCode());

    assertThat(test1).isNotEqualTo(test3);
    assertThat(test1).isNotEqualTo(test4);
    assertThat(test1).isNotEqualTo(test5);
    assertThat(test1).isNotEqualTo(null);
    assertThat(test1).isNotEqualTo("some string");
  }

  @Test
  void shouldImplementToString() {
    SlowTest slowTest = new SlowTest("com.example.Test", "testMethod", 1500.5);
    assertThat(slowTest.toString()).isEqualTo("com.example.Test#testMethod (1500.5ms)");
  }
}
