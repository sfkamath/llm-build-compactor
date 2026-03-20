package io.llmcompactor.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentContextWriterTest {

  @TempDir Path tempDir;

  @Test
  void shouldWriteContextFile() throws IOException {
    Path path = tempDir.resolve("agent-context.json");
    Map<String, Object> map = new HashMap<>();
    map.put("key", "value");
    AgentContextWriter.write(map, path);

    assertThat(path).exists();
    String content = new String(Files.readAllBytes(path));
    assertThat(content).contains("\"key\" : \"value\"");
  }
}
