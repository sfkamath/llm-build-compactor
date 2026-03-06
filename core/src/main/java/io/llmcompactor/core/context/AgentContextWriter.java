package io.llmcompactor.core.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Map;

public final class AgentContextWriter {

  private static final ObjectMapper mapper = new ObjectMapper();

  public static void write(Map<String, Object> ctx, Path path) {

    try {

      mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), ctx);

    } catch (Exception e) {

      throw new RuntimeException(e);
    }
  }

  private AgentContextWriter() {}
}
