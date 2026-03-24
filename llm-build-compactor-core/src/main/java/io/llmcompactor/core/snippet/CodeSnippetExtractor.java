package io.llmcompactor.core.snippet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CodeSnippetExtractor {

  public static String extract(Path file, int line) {
    try {

      List<String> lines = Files.readAllLines(file);

      int start = Math.max(0, line - 3);
      int end = Math.min(lines.size(), line + 3);

      StringBuilder snippet = new StringBuilder();

      for (int i = start; i < end; i++) {
        snippet.append(lines.get(i)).append("\n");
      }

      return snippet.toString();

    } catch (IOException e) {
      return "";
    }
  }

  private CodeSnippetExtractor() {}
}
