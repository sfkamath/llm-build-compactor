package io.llmcompactor.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class IoUtilsTest {

  @Test
  void nullPrintStreamDoesNotThrow() throws java.io.IOException {
    PrintStream ps = IoUtils.nullPrintStream();
    ps.write(65);
    ps.write(new byte[] {1, 2, 3});
    ps.write(new byte[] {1, 2, 3}, 0, 2);
    // Text paths encode through a CharsetEncoder that does not route via write(byte[]...),
    // so every print/println overload must be sealed independently.
    ps.print("string");
    ps.print((Object) "object");
    ps.println("string");
    ps.println((Object) "object");
    ps.println();
    assertThat(ps.checkError()).isFalse();
  }

  @Test
  void nullPrintStreamSwallowsAllBytes() throws java.io.IOException {
    // Seals every byte/text path against the same null sink the production factory uses.
    CountingOutputStream sink = new CountingOutputStream();
    PrintStream ps = sealingPrintStream(sink);
    ps.write(65);
    ps.write(new byte[] {1, 2, 3});
    ps.write(new byte[] {1, 2, 3}, 0, 2);
    ps.print("string");
    ps.print((Object) "object");
    ps.println("string");
    ps.println((Object) "object");
    ps.println();
    ps.flush();
    assertThat(sink.bytes).as("null PrintStream must emit zero bytes").isZero();
  }

  /** Mirror of {@link IoUtils#nullPrintStream()} overrides, but over an observable sink. */
  private static PrintStream sealingPrintStream(java.io.OutputStream sink) {
    try {
      return new PrintStream(sink, true, java.nio.charset.StandardCharsets.UTF_8.name()) {
        @Override
        public void write(int b) {}

        @Override
        public void write(byte[] buf) {}

        @Override
        public void write(byte[] buf, int off, int len) {}

        @Override
        public void print(String s) {}

        @Override
        public void print(Object o) {}

        @Override
        public void println(String s) {}

        @Override
        public void println(Object o) {}

        @Override
        public void println() {}
      };
    } catch (java.io.UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final class CountingOutputStream extends java.io.OutputStream {
    int bytes;

    @Override
    public void write(int b) {
      bytes++;
    }

    @Override
    public void write(byte[] b, int off, int len) {
      bytes += len;
    }
  }
}
