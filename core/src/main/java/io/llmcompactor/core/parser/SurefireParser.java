package io.llmcompactor.core.parser;

import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.StackTraceCompressor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SurefireParser {

    private static final Pattern LINE_NUMBER_PATTERN = Pattern.compile("\\.java:(\\d+)\\)");

    public static TestResult parse(Path targetDir, boolean compressStackFrames, List<String> includePackages) {
        List<BuildError> failures = new ArrayList<>();
        AtomicInteger totalTests = new AtomicInteger(0);
        AtomicInteger testFailures = new AtomicInteger(0);

        Path reportsDir = targetDir.resolve("surefire-reports");
        if (!Files.exists(reportsDir)) {
            reportsDir = targetDir.resolve("failsafe-reports");
        }

        if (Files.exists(reportsDir)) {
            try (Stream<Path> files = Files.list(reportsDir)) {
                files.filter(p -> p.toString().endsWith(".xml"))
                     .forEach(file -> {
                         try {
                             DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                             DocumentBuilder builder = factory.newDocumentBuilder();
                             Document doc = builder.parse(file.toFile());

                             String tests = doc.getDocumentElement().getAttribute("tests");
                             totalTests.addAndGet(Integer.parseInt(tests));

                             var failureNodes = doc.getElementsByTagName("failure");
                             for (int i = 0; i < failureNodes.getLength(); i++) {
                                 var node = failureNodes.item(i);
                                 String type = ((Element) node).getAttribute("type");
                                 String message = node.getTextContent();
                                 
                                 // Attempt to find original file/line from stack trace
                                 String sourceFile = null;
                                 int line = -1;
                                 
                                 // Try to find the first project frame
                                 String[] lines = message.split("\n");
                                 for (String l : lines) {
                                     if (l.contains(".java:") && !isFrameworkFrame(l, includePackages)) {
                                         Matcher m = LINE_NUMBER_PATTERN.matcher(l);
                                         if (m.find()) {
                                             line = Integer.parseInt(m.group(1));
                                             // Find the class/file name from the frame
                                             int atIndex = l.indexOf("at ");
                                             int parenIndex = l.indexOf("(");
                                             if (atIndex >= 0 && parenIndex > atIndex) {
                                                 String fullClass = l.substring(atIndex + 3, parenIndex);
                                                 int lastDotInClass = fullClass.lastIndexOf(".");
                                                 if (lastDotInClass > 0) {
                                                     String packageName = fullClass.substring(0, lastDotInClass);
                                                     sourceFile = "src/test/java/" + packageName.replace(".", "/") + "/" + l.substring(parenIndex + 1, l.indexOf(".java:") + 5);
                                                 }
                                             }
                                             break; 
                                         }
                                     }
                                 }

                                 String stackTrace = compressStackFrames ? StackTraceCompressor.compress(message, null, includePackages) : message;

                                 failures.add(new BuildError(
                                         type,
                                         sourceFile != null ? sourceFile : file.getFileName().toString(),
                                         line,
                                         extractFirstLine(message),
                                         stackTrace
                                 ));
                                 testFailures.incrementAndGet();
                             }

                             var errorNodes = doc.getElementsByTagName("error");
                             for (int i = 0; i < errorNodes.getLength(); i++) {
                                 var node = errorNodes.item(i);
                                 String type = ((Element) node).getAttribute("type");
                                 String message = node.getTextContent();
                                 
                                 String sourceFile = null;
                                 int line = -1;
                                 
                                 String[] lines = message.split("\n");
                                 for (String l : lines) {
                                     if (l.contains(".java:") && !isFrameworkFrame(l, includePackages)) {
                                         Matcher m = LINE_NUMBER_PATTERN.matcher(l);
                                         if (m.find()) {
                                             line = Integer.parseInt(m.group(1));
                                             int atIndex = l.indexOf("at ");
                                             int parenIndex = l.indexOf("(");
                                             if (atIndex >= 0 && parenIndex > atIndex) {
                                                 String fullClass = l.substring(atIndex + 3, parenIndex);
                                                 int lastDotInClass = fullClass.lastIndexOf(".");
                                                 if (lastDotInClass > 0) {
                                                     String packageName = fullClass.substring(0, lastDotInClass);
                                                     sourceFile = "src/test/java/" + packageName.replace(".", "/") + "/" + l.substring(parenIndex + 1, l.indexOf(".java:") + 5);
                                                 }
                                             }
                                             break;
                                         }
                                     }
                                 }

                                 String stackTrace = compressStackFrames ? StackTraceCompressor.compress(message, null, includePackages) : message;

                                 failures.add(new BuildError(
                                         type,
                                         sourceFile != null ? sourceFile : file.getFileName().toString(),
                                         line,
                                         extractFirstLine(message),
                                         stackTrace
                                 ));
                                 testFailures.incrementAndGet();
                             }

                         } catch (Exception ignored) {}
                     });
            } catch (Exception ignored) {}
        }

        return new TestResult(totalTests.get(), testFailures.get(), failures);
    }

    private static String extractFirstLine(String message) {
        if (message == null || message.isEmpty()) return "";
        return message.split("\n")[0].trim();
    }

    private static boolean isFrameworkFrame(String line, List<String> includePackages) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("at ")) return false;

        // Explicit inclusion overrides framework detection
        if (includePackages != null) {
            for (String pkg : includePackages) {
                if (trimmed.contains("at " + pkg)) {
                    return false; // It's not a framework frame (we want to keep it)
                }
            }
        }
        
        String[] frameworkPrefixes = {
            "org.junit.", "org.opentest4j.", "java.", "javax.", "sun.", "com.sun.", "jdk.",
            "org.apache.maven.", "org.gradle.", "org.springframework.", "org.hibernate.",
            "io.projectreactor.", "reactor.core.", "io.micronaut."
        };
        
        for (String prefix : frameworkPrefixes) {
            if (trimmed.contains("at " + prefix)) {
                return true;
            }
        }
        return false;
    }
}
