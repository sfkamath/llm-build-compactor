package io.llmcompactor.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

public class SlowTest {
  private final String className;
  private final String testName;
  private final double testDuration;

  public SlowTest(String className, String testName, double testDuration) {
    this.className = className;
    this.testName = testName;
    this.testDuration = testDuration;
  }

  public String className() {
    return className;
  }

  public String testName() {
    return testName;
  }

  public double testDuration() {
    return testDuration;
  }

  public String getClassName() {
    return className;
  }

  public String getTestName() {
    return testName;
  }

  @JsonInclude(JsonInclude.Include.NON_DEFAULT)
  public double getTestDuration() {
    return testDuration;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SlowTest that = (SlowTest) o;
    return Double.compare(that.testDuration, testDuration) == 0
        && Objects.equals(className, that.className)
        && Objects.equals(testName, that.testName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(className, testName, testDuration);
  }

  @Override
  public String toString() {
    return className + "#" + testName + " (" + testDuration + "ms)";
  }
}
