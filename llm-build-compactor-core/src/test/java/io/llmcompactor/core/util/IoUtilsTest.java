package io.llmcompactor.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class IoUtilsTest {

  @Test
  void nullPrintStreamDoesNotThrow() {
    PrintStream ps = IoUtils.nullPrintStream();
    ps.write(65);
    ps.write(new byte[] {1, 2, 3}, 0, 2);
    ps.println("test");
    assertThat(ps.checkError()).isFalse();
  }
}
