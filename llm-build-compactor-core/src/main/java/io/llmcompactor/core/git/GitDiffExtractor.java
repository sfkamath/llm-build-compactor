package io.llmcompactor.core.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
public class GitDiffExtractor {

  public static List<String> changedFiles() {

    // Use a set to deduplicate files touched across recent commits
    Set<String> seen = new LinkedHashSet<>();

    Process p;
    try {
      // --pretty=format: emits no commit header lines; --name-only lists the files.
      // -n 10 caps the look-back to the last 10 commits so the list stays concise.
      p =
          new ProcessBuilder("git", "log", "--name-only", "--pretty=format:", "-n", "10", "HEAD")
              .start();
    } catch (IOException ignored) {
      // Best effort, ignore if git is not available or not a repo
      return new ArrayList<>(seen);
    }

    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {

      String line;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.trim();
        if (!trimmed.isEmpty()) {
          seen.add(trimmed);
        }
      }
      p.waitFor();
    } catch (IOException | InterruptedException ignored) {
      if (Thread.interrupted()) {
        Thread.currentThread().interrupt();
      }
    }

    return new ArrayList<>(seen);
  }
}
