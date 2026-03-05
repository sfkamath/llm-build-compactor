package io.llmcompactor.core.parser;

import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.StackTraceCompressor;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SurefireParser {

    // Match: at package.ClassName.methodName(FileName.java:line)
    // Group 1: full class name with method (e.g., io.llmcompactor.testbed.OrderServiceTest.testOrderWithDiscount)
    // Group 2: file name (e.g., OrderServiceTest.java)
    // Group 3: line number (e.g., 24)
    private static final Pattern STACK_TRACE_LINE = Pattern.compile("at\\s+([\\w.$]+)\\((\\w+\\.java):(\\d+)\\)");

    public static TestResult parse(Path reportsDir) {

        List<BuildError> failures = new ArrayList<>();
        AtomicInteger testsRun = new AtomicInteger(0);
        AtomicInteger testFailures = new AtomicInteger(0);

        Path surefireDir = reportsDir.resolve("surefire-reports");
        Path failsafeDir = reportsDir.resolve("failsafe-reports");

        parseReports(surefireDir, testsRun, testFailures, failures);
        parseReports(failsafeDir, testsRun, testFailures, failures);

        return new TestResult(testsRun.get(), testFailures.get(), failures);
    }

    private static void parseReports(Path reportsDir, AtomicInteger testsRun,
                                      AtomicInteger testFailures, List<BuildError> failures) {
        if (!Files.exists(reportsDir)) {
            return;
        }

        try {

            Files.list(reportsDir)
                    .filter(p -> p.toString().endsWith(".xml"))
                    .forEach(file -> {

                        try {

                            var doc = DocumentBuilderFactory
                                    .newInstance()
                                    .newDocumentBuilder()
                                    .parse(file.toFile());

                            var testcaseNodes = doc.getElementsByTagName("testcase");
                            testsRun.addAndGet(testcaseNodes.getLength());

                            var failureNodes = doc.getElementsByTagName("failure");

                            for (int i = 0; i < failureNodes.getLength(); i++) {

                                var node = failureNodes.item(i);
                                String message = node.getTextContent();
                                String type = "TEST_FAILURE";

                                if (node instanceof Element) {
                                    String attrType = ((Element) node).getAttribute("type");
                                    if (attrType != null && !attrType.isEmpty()) {
                                        type = attrType;
                                    }
                                }

                                String[] fileAndLine = extractFileAndLine(message);
                                String sourceFile = fileAndLine[0];
                                int line = Integer.parseInt(fileAndLine[1]);

                                String compressed = StackTraceCompressor.compress(message, null);
                                String finalMessage = compressed.isEmpty() ? message.trim() : compressed;

                                failures.add(new BuildError(
                                        type,
                                        sourceFile != null ? sourceFile : file.getFileName().toString(),
                                        line,
                                        type + ": " + finalMessage
                                ));
                                testFailures.incrementAndGet();

                            }

                            var errorNodes = doc.getElementsByTagName("error");

                            for (int i = 0; i < errorNodes.getLength(); i++) {

                                var node = errorNodes.item(i);
                                String message = node.getTextContent();
                                String type = "TEST_ERROR";

                                if (node instanceof Element) {
                                    String attrType = ((Element) node).getAttribute("type");
                                    if (attrType != null && !attrType.isEmpty()) {
                                        type = attrType;
                                    }
                                }

                                String[] fileAndLine = extractFileAndLine(message);
                                String sourceFile = fileAndLine[0];
                                int line = Integer.parseInt(fileAndLine[1]);

                                String compressed = StackTraceCompressor.compress(message, null);
                                String finalMessage = compressed.isEmpty() ? message.trim() : compressed;

                                failures.add(new BuildError(
                                        type,
                                        sourceFile != null ? sourceFile : file.getFileName().toString(),
                                        line,
                                        type + ": " + finalMessage
                                ));
                                testFailures.incrementAndGet();

                            }

                        } catch (Exception ignored) {}

                    });

        } catch (Exception ignored) {}
    }

    private static String[] extractFileAndLine(String message) {
        // Find the first project frame (io.llmcompactor.*)
        Matcher matcher = STACK_TRACE_LINE.matcher(message);
        while (matcher.find()) {
            String fullClassName = matcher.group(1);  // e.g., io.llmcompactor.testbed.OrderServiceTest.testOrderWithDiscount
            String fileName = matcher.group(2);        // e.g., OrderServiceTest.java
            String lineNum = matcher.group(3);         // e.g., 24

            if (fullClassName.startsWith("io.llmcompactor.")) {
                // Extract class name without method: remove the last segment after the final dot
                // e.g., "io.llmcompactor.testbed.OrderServiceTest.testOrderWithDiscount" -> "io.llmcompactor.testbed.OrderServiceTest"
                int lastDot = fullClassName.lastIndexOf('.');
                if (lastDot > 0) {
                    String className = fullClassName.substring(0, lastDot);
                    
                    // Convert package to path: io.llmcompactor.testbed -> io/llmcompactor/testbed
                    int pkgEnd = className.lastIndexOf('.');
                    if (pkgEnd > 0) {
                        String packagePath = className.substring(0, pkgEnd).replace('.', '/');

                        // Check in order: test, it, main
                        String sourcePath = "src/test/java/" + packagePath + "/" + fileName;
                        if (!Files.exists(Path.of(sourcePath))) {
                            sourcePath = "src/it/java/" + packagePath + "/" + fileName;
                        }
                        if (!Files.exists(Path.of(sourcePath))) {
                            sourcePath = "src/main/java/" + packagePath + "/" + fileName;
                        }

                        return new String[]{sourcePath, lineNum};
                    }
                }
                return new String[]{null, "-1"};
            }
        }
        return new String[]{null, "-1"};
    }
}
