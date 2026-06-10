package io.llmcompactor.core;

public final class CompactorDefaults {

  private CompactorDefaults() {}

  public static boolean resolveEnabled(boolean llmcePresent, String enabledPropertyValue) {
    if (llmcePresent) {
      return false;
    }
    return !"false".equalsIgnoreCase(enabledPropertyValue);
  }
}
