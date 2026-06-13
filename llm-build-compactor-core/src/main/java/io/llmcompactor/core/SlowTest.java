package io.llmcompactor.core;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class SlowTest {
  String className;
  String testName;

  @JsonInclude(JsonInclude.Include.NON_DEFAULT)
  double testDuration;

  @Override
  public String toString() {
    return className + "#" + testName + " (" + testDuration + "ms)";
  }
}
