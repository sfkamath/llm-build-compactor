package io.llmcompactor.core.extract;

import static org.assertj.core.api.Assertions.assertThat;

import io.llmcompactor.core.BuildError;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompilationErrorExtractorTest {

  @Test
  void shouldExtractSymbolLocationDetails() {
    List<String> logs =
        Arrays.asList(
            "[INFO] --- compiler:3.13.0:compile (default-compile) @ project ---",
            "[ERROR] /path/to/StrategyPatternArchitectureTest.java:[190,13] error: cannot find symbol",
            "  symbol:   method and()",
            "  location: interface com.tngtech.archunit.lang.syntax.elements.ClassesShouldConjunction");

    List<BuildError> errors = CompilationErrorExtractor.extract(logs);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).file()).isEqualTo("/path/to/StrategyPatternArchitectureTest.java");
    assertThat(errors.get(0).lines()).containsExactly(190);
    assertThat(errors.get(0).message())
        .isEqualTo(
            "error: cannot find symbol\n  symbol:   method and()\n  location: interface com.tngtech.archunit.lang.syntax.elements.ClassesShouldConjunction");
  }

  @Test
  void shouldExtractErrorsFromMavenLogs() {
    List<String> logs =
        Arrays.asList(
            "[INFO] --- compiler:3.13.0:compile (default-compile) @ core ---",
            "[ERROR] /Users/sfk/Developer/project/src/main/java/io/App.java:[20,49] error: method compress in class StackTraceCompressor cannot be applied to given types;",
            "[ERROR] /Users/sfk/Developer/project/src/main/java/io/Service.java:[10,5] error: cannot find symbol",
            "[INFO] 2 errors");

    List<BuildError> errors = CompilationErrorExtractor.extract(logs);

    assertThat(errors).hasSize(2);

    assertThat(errors.get(0).file())
        .isEqualTo("/Users/sfk/Developer/project/src/main/java/io/App.java");
    assertThat(errors.get(0).lines()).containsExactly(20);
    assertThat(errors.get(0).message())
        .isEqualTo(
            "error: method compress in class StackTraceCompressor cannot be applied to given types;");
    assertThat(errors.get(0).type()).isEqualTo("COMPILATION_ERROR");

    assertThat(errors.get(1).file())
        .isEqualTo("/Users/sfk/Developer/project/src/main/java/io/Service.java");
    assertThat(errors.get(1).lines()).containsExactly(10);
    assertThat(errors.get(1).message()).isEqualTo("error: cannot find symbol");
  }

  @Test
  void shouldReturnEmptyListIfNoErrorsFound() {
    List<String> logs =
        Arrays.asList(
            "[INFO] --- compiler:3.13.0:compile (default-compile) @ core ---",
            "[INFO] Nothing to compile - all classes are up to date");

    List<BuildError> errors = CompilationErrorExtractor.extract(logs);

    assertThat(errors).isEmpty();
  }

  @Test
  void shouldExtractFatalErrorWithAnsiCodes() {
    List<String> logs =
        Arrays.asList(
            "[INFO] --- compiler:3.15.0:compile (default-compile) @ tui-sessions-app ---",
            "[ERROR] Failed to execute goal [32mmaven-compiler-plugin:3.15.0:compile[m [1m(default-compile)[m on project [36mtui-sessions-app[m: [1;31mFatal error compiling[m",
            "[1;31mFatal error compiling: java.lang.IllegalArgumentException: The argument does not represent an annotation type: Singleton[m");

    List<BuildError> errors = CompilationErrorExtractor.extract(logs);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).file()).isEqualTo("pom.xml");
    assertThat(errors.get(0).lines()).containsExactly(1);
    assertThat(errors.get(0).message())
        .contains("The argument does not represent an annotation type: Singleton");
    assertThat(errors.get(0).type()).isEqualTo("COMPILATION_ERROR");
  }
}
