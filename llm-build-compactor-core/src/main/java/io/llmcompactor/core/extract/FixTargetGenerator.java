package io.llmcompactor.core.extract;

import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.FixTarget;
import io.llmcompactor.core.SummaryWriter;
import io.llmcompactor.core.snippet.CodeSnippetExtractor;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
public class FixTargetGenerator {

  public static List<FixTarget> generate(List<BuildError> errors) {

    List<FixTarget> targets = new ArrayList<>();
    Set<String> seen = new HashSet<>();

    for (BuildError error : errors) {

      if (error.file() == null || error.lines() == null || error.lines().isEmpty()) {
        continue;
      }

      int line = error.lines().get(0);
      String key = error.file() + ":" + line;
      if (!seen.add(key)) {
        continue;
      }

      // For test files, snippets show the assertion line which is not actionable
      // The message already describes what failed. Skip snippets for test files.
      String fileName = error.file();
      boolean isTestFile =
          fileName.contains("/test/")
              || fileName.contains("/it/")
              || fileName.endsWith("Test.java")
              || fileName.endsWith("IT.java")
              || fileName.endsWith("Tests.java")
              || fileName.endsWith("Spec.groovy")
              || fileName.endsWith("Spec.java");

      String snippet = isTestFile ? null : CodeSnippetExtractor.extract(Paths.get(fileName), line);
      String reason = SummaryWriter.stripExceptionPackage(error.message());

      targets.add(new FixTarget(error.file(), line, reason, snippet));
    }

    return targets;
  }
}
