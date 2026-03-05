package io.llmcompactor.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;

public class SummaryWriter {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void write(BuildSummary summary, Path output) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), summary);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String toJson(BuildSummary summary) {
        try {
            return mapper.writeValueAsString(summary);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
