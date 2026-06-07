package io.llmcompactor.core;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

public final class CompactorDefaults {

  private CompactorDefaults() {}

  public static boolean resolveEnabled(boolean llmcePresent, String enabledPropertyValue) {
    if (llmcePresent) {
      return false;
    }
    return !"false".equalsIgnoreCase(enabledPropertyValue);
  }

  public static PrintStream nullPrintStream() {
    OutputStream nullOut =
        new OutputStream() {
          @Override
          public void write(int b) {}
        };
    try {
      return new PrintStream(nullOut, true, StandardCharsets.UTF_8.name()) {
        @Override
        public void write(byte[] buf, int off, int len) {}
      };
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("UTF-8 not supported", e);
    }
  }
}
