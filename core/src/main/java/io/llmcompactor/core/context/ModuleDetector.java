package io.llmcompactor.core.context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModuleDetector {

    public static List<String> detectModules(Path root) {

        List<String> modules = new ArrayList<>();

        try {

            Files.list(root)
                    .filter(p -> Files.exists(p.resolve("pom.xml")))
                    .forEach(p -> modules.add(p.getFileName().toString()));

        } catch (Exception ignored) {}

        return modules;

    }

}
