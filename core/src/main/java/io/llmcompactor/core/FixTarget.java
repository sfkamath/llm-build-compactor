package io.llmcompactor.core;

import java.util.Objects;

public class FixTarget {
  private final String file;
  private final int line;
  private final String reason;
  private final String snippet;

  public FixTarget(String file, int line, String reason, String snippet) {
    this.file = file;
    this.line = line;
    this.reason = reason;
    this.snippet = snippet;
  }

  public String file() {
    return file;
  }

  public int line() {
    return line;
  }

  public String reason() {
    return reason;
  }

  public String snippet() {
    return snippet;
  }

  // Jackson getters
  public String getFile() {
    return file;
  }

  public int getLine() {
    return line;
  }

  public String getReason() {
    return reason;
  }

  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  public String getSnippet() {
    return snippet;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FixTarget fixTarget = (FixTarget) o;
    return line == fixTarget.line
        && Objects.equals(file, fixTarget.file)
        && Objects.equals(reason, fixTarget.reason)
        && Objects.equals(snippet, fixTarget.snippet);
  }

  @Override
  public int hashCode() {
    return Objects.hash(file, line, reason, snippet);
  }

  @Override
  public String toString() {
    return "FixTarget{file='" + file + "', line=" + line + ", reason='" + reason + "'}";
  }
}
