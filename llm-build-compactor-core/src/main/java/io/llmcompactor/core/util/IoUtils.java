package io.llmcompactor.core.util;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import lombok.experimental.UtilityClass;

@UtilityClass
public class IoUtils {

  public static PrintStream nullPrintStream() {
    OutputStream nullOut =
        new OutputStream() {
          @Override
          public void write(int b) {}

          @Override
          public void write(byte[] b) {}

          @Override
          public void write(byte[] b, int off, int len) {}
        };
    try {
      // Override every text path, not just write(...): PrintStream's print/println encode
      // through a CharsetEncoder that does NOT route via the overridden write(byte[]...),
      // so unsealed print(String)/println(Object) can leak past a write-only null stream.
      return new PrintStream(nullOut, true, StandardCharsets.UTF_8.name()) {
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
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("UTF-8 not supported", e);
    }
  }
}
