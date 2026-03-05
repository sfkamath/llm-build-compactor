package io.llmcompactor.core;

import java.util.ArrayList;
import java.util.List;

public class StackTraceCompressor {

    private static final List<String> FRAMEWORK_PREFIXES = List.of(
            "java.", "javax.", "sun.", "com.sun.",
            "org.junit.", "org.testng.", "org.apache.maven.",
            "org.gradle.", "org.springframework.", "org.hibernate."
    );

    public static String compress(String stackTrace, String projectPackage) {
        if (stackTrace == null || stackTrace.isEmpty()) {
            return "";
        }

        String[] lines = stackTrace.split("\n");
        List<String> projectFrames = new ArrayList<>();
        List<String> frameworkFrames = new ArrayList<>();

        for (String line : lines) {
            if (line.trim().startsWith("at")) {
                if (isProjectFrame(line, projectPackage)) {
                    projectFrames.add(line);
                } else if (isFrameworkFrame(line)) {
                    frameworkFrames.add(line);
                }
            }
        }

        StringBuilder result = new StringBuilder();

        if (!projectFrames.isEmpty()) {
            result.append("Project frames:\n");
            for (String frame : projectFrames) {
                result.append("  ").append(frame).append("\n");
            }
        }

        if (!frameworkFrames.isEmpty()) {
            result.append("\nFramework frames (truncated):\n");
            int show = Math.min(5, frameworkFrames.size());
            for (int i = 0; i < show; i++) {
                result.append("  ").append(frameworkFrames.get(i)).append("\n");
            }
            if (frameworkFrames.size() > 5) {
                result.append("  ... and ").append(frameworkFrames.size() - 5).append(" more\n");
            }
        }

        return result.toString();
    }

    private static boolean isProjectFrame(String line, String projectPackage) {
        if (projectPackage != null && !projectPackage.isEmpty()) {
            return line.contains(projectPackage);
        }
        for (String prefix : FRAMEWORK_PREFIXES) {
            if (line.contains("at " + prefix)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFrameworkFrame(String line) {
        for (String prefix : FRAMEWORK_PREFIXES) {
            if (line.contains("at " + prefix)) {
                return true;
            }
        }
        return false;
    }

    public static String findProjectFrame(List<String> frames, String groupId) {
        return frames.stream()
                .filter(f -> f.contains(groupId))
                .findFirst()
                .orElse(null);
    }

}
