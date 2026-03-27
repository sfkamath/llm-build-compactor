package io.llmcompactor.core.snippet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeSnippetExtractorTest {

  @TempDir Path tempDir;

  @Test
  void shouldExtractSnippetAroundLine() throws IOException {
    Path sourceFile = tempDir.resolve("App.java");
    String content =
        "line 1\n"
            + "line 2\n"
            + "line 3\n"
            + "line 4\n"
            + "line 5\n"
            + "line 6\n"
            + "line 7\n"
            + "line 8\n"
            + "line 9\n"
            + "line 10\n";
    Files.write(sourceFile, content.getBytes(), StandardOpenOption.CREATE);

    // Context is +/- 3 lines.
    // Line 5 is index 4. Start = max(0, 5-3) = 2 (line 3). End = min(10, 5+3) = 8 (line 9).

    String snippet = CodeSnippetExtractor.extract(sourceFile, 5);

    assertThat(snippet).contains("line 5");
    assertThat(snippet).contains("line 3");
    assertThat(snippet).contains("line 7");
    assertThat(snippet).doesNotContain("line 1");
    assertThat(snippet)
        .doesNotContain("line 9"); // End is exclusive in loop: for (int i = start; i < end; i++)
    // Wait, index 7 is line 8. So it should have line 3, 4, 5, 6, 7, 8.
  }

  @Test
  void shouldReturnEmptyStringIfFileNotFound() {
    String snippet = CodeSnippetExtractor.extract(tempDir.resolve("NonExistent.java"), 1);
    assertThat(snippet).isEmpty();
  }
}
