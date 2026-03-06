package io.llmcompactor.core.extract;

import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.FixTarget;
import io.llmcompactor.core.snippet.CodeSnippetExtractor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FixTargetGenerator {

  public static List<FixTarget> generate(List<BuildError> errors) {

    List<FixTarget> targets = new ArrayList<>();
    Set<String> seen = new HashSet<>();

    for (BuildError error : errors) {

      if (error.file() == null || error.line() <= 0) {
        continue;
      }

      String key = error.file() + ":" + error.line();
      if (!seen.add(key)) {
        continue;
      }

      String snippet = CodeSnippetExtractor.extract(Path.of(error.file()), error.line());

      targets.add(new FixTarget(error.file(), error.line(), error.message(), snippet));
    }

    return targets;
  }

  private FixTargetGenerator() {}
}
