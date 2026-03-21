package io.llmcompactor.core.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GitDiffExtractor {

  public static List<String> changedFiles() {

    // Use a set to deduplicate files touched across recent commits
    Set<String> seen = new LinkedHashSet<String>();

    try {

      // --pretty=format: emits no commit header lines; --name-only lists the files.
      // -n 10 caps the look-back to the last 10 commits so the list stays concise.
      Process p =
          new ProcessBuilder("git", "log", "--name-only", "--pretty=format:", "-n", "10", "HEAD")
              .start();

      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {

        String line;

        while ((line = reader.readLine()) != null) {
          String trimmed = line.trim();
          if (!trimmed.isEmpty()) {
            seen.add(trimmed);
          }
        }
      }

      p.waitFor();

    } catch (IOException | InterruptedException ignored) {
      // Best effort, ignore if git is not available or not a repo
      if (Thread.interrupted()) {
        Thread.currentThread().interrupt();
      }
    }

    return new ArrayList<String>(seen);
  }

  private GitDiffExtractor() {}
}
