package io.llmcompactor.core.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class GitDiffExtractor {

  public static List<String> changedFiles() {

    List<String> files = new ArrayList<>();

    try {

      Process p = new ProcessBuilder("git", "diff", "--name-only", "HEAD").start();

      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {

        String line;

        while ((line = reader.readLine()) != null) {
          files.add(line.trim());
        }
      }

      p.waitFor();

    } catch (IOException | InterruptedException ignored) {
      // Best effort, ignore if git is not available or not a repo
      if (Thread.interrupted()) {
        Thread.currentThread().interrupt();
      }
    }

    return files;
  }

  private GitDiffExtractor() {}
}
