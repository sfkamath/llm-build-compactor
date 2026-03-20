package io.llmcompactor.core.parser;

import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.StackTraceCompressor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public final class SurefireParser {

  private static final Pattern LINE_NUMBER_PATTERN = Pattern.compile("\\.java:(\\d+)\\)");

  public static TestResult parse(
      Path targetDir,
      boolean compressStackFrames,
      List<String> includePackages,
      long sessionStartTime,
      boolean showFailedTestLogs) {
    List<BuildError> failures = new ArrayList<>();
    List<Double> allDurations = new ArrayList<>();
    AtomicInteger totalTests = new AtomicInteger(0);
    AtomicInteger testFailures = new AtomicInteger(0);

    List<Path> reportDirs =
        Arrays.asList(targetDir.resolve("surefire-reports"), targetDir.resolve("failsafe-reports"));

    for (Path reportsDir : reportDirs) {
      if (Files.exists(reportsDir)) {
        try (Stream<Path> files = Files.list(reportsDir)) {
          files
              .filter(p -> p.toString().endsWith(".xml"))
              .filter(p -> p.toFile().lastModified() >= sessionStartTime)
              .forEach(
                  file -> {
                    try {
                      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                      DocumentBuilder builder = factory.newDocumentBuilder();
                      Document doc = builder.parse(file.toFile());

                      String tests = doc.getDocumentElement().getAttribute("tests");
                      if (tests != null && !tests.isEmpty()) {
                        totalTests.addAndGet(Integer.parseInt(tests));
                      }

                      // Collect all durations
                      NodeList testCaseNodes = doc.getElementsByTagName("testcase");
                      for (int i = 0; i < testCaseNodes.getLength(); i++) {
                        Element testCase = (Element) testCaseNodes.item(i);
                        String timeAttr = testCase.getAttribute("time");
                        if (timeAttr != null && !timeAttr.isEmpty()) {
                          try {
                            allDurations.add(Double.parseDouble(timeAttr));
                          } catch (NumberFormatException e) {
                            // Ignore
                          }
                        }
                      }

                      NodeList failureNodes = doc.getElementsByTagName("failure");
                      for (int i = 0; i < failureNodes.getLength(); i++) {
                        Node node = failureNodes.item(i);
                        String type = ((Element) node).getAttribute("type");
                        String message = node.getTextContent().trim();

                        double duration = getTestDuration(node);
                        String testLogs =
                            showFailedTestLogs ? readTestLogs(file, reportsDir) : null;
                        BuildError error =
                            parseError(
                                message,
                                type,
                                file,
                                includePackages,
                                compressStackFrames,
                                duration,
                                testLogs);
                        failures.add(error);
                        testFailures.incrementAndGet();
                      }

                      NodeList errorNodes = doc.getElementsByTagName("error");
                      for (int i = 0; i < errorNodes.getLength(); i++) {
                        Node node = errorNodes.item(i);
                        String type = ((Element) node).getAttribute("type");
                        String message = node.getTextContent().trim();

                        double duration = getTestDuration(node);
                        String testLogs =
                            showFailedTestLogs ? readTestLogs(file, reportsDir) : null;
                        BuildError error =
                            parseError(
                                message,
                                type,
                                file,
                                includePackages,
                                compressStackFrames,
                                duration,
                                testLogs);
                        failures.add(error);
                        testFailures.incrementAndGet();
                      }

                    } catch (ParserConfigurationException | SAXException | IOException e) {
                      // Ignore corrupt XML
                    }
                  });
        } catch (IOException e) {
          // Ignore IO errors
        }
      }
    }

    return new TestResult(totalTests.get(), testFailures.get(), failures, allDurations);
  }

  private static String resolveSourceFile(String packageName, String fileName) {
    String relativePath = packageName.replace(".", "/") + "/" + fileName;
    String[] roots = {
      "src/main/java/", "src/test/java/", "src/it/java/", "src/integration-test/java/"
    };

    for (String root : roots) {
      String fullPath = root + relativePath;
      if (Files.exists(Paths.get(fullPath))) {
        return fullPath;
      }
    }
    // Default to src/test/java if not found
    return "src/test/java/" + relativePath;
  }

  private static String extractFirstLine(String message) {
    if (message == null || message.isEmpty()) {
      return "";
    }
    return message.split("\n")[0].trim();
  }

  private static double getTestDuration(Node node) {
    double duration = 0.0;
    Node parentNode = node.getParentNode();
    if (parentNode instanceof Element) {
      Element testCase = (Element) parentNode;
      String timeAttr = testCase.getAttribute("time");
      if (timeAttr != null && !timeAttr.isEmpty()) {
        try {
          duration = Double.parseDouble(timeAttr);
        } catch (NumberFormatException e) {
          // Ignore
        }
      }
    }
    return duration;
  }

  private static String readTestLogs(Path xmlFile, Path reportsDir) {
    try {
      Path fileName = xmlFile.getFileName();
      if (fileName == null) {
        return null;
      }
      String xmlFileName = fileName.toString();
      // XML files are named TEST-ClassName.xml, output files are ClassName-output.txt
      String baseName = xmlFileName.replace(".xml", "");
      if (baseName.startsWith("TEST-")) {
        baseName = baseName.substring(5);
      }
      // Surefire uses ClassName-output.txt for test output
      Path logFile = reportsDir.resolve(baseName + "-output.txt");
      if (Files.exists(logFile)) {
        return new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8);
      }
    } catch (IOException e) {
      // Ignore
    }
    return null;
  }

  private static BuildError parseError(
      String message,
      String type,
      Path file,
      List<String> includePackages,
      boolean compressStackFrames,
      double duration,
      String testLogs) {
    String sourceFile = null;
    int line = -1;

    // Try to find the last project frame (the actual test method)
    String[] lines = message.split("\n");
    String lastProjectFrame = null;
    for (String l : lines) {
      if (l.contains(".java:") && !isFrameworkFrame(l, includePackages)) {
        lastProjectFrame = l;
      }
    }

    // Parse the last project frame to extract file and line
    if (lastProjectFrame != null) {
      Matcher m = LINE_NUMBER_PATTERN.matcher(lastProjectFrame);
      if (m.find()) {
        line = Integer.parseInt(m.group(1));
        int atIndex = lastProjectFrame.indexOf("at ");
        int parenIndex = lastProjectFrame.indexOf("(");
        int javaIndex = lastProjectFrame.indexOf(".java:");
        if (atIndex >= 0 && parenIndex > atIndex && javaIndex > parenIndex) {
          String fullClass = lastProjectFrame.substring(atIndex + 3, parenIndex);
          int lastDotInClass = fullClass.lastIndexOf(".");
          if (lastDotInClass > 0) {
            String packageName = fullClass.substring(0, lastDotInClass);
            String fileName = lastProjectFrame.substring(parenIndex + 1, javaIndex + 5);
            int classNameEnd = fileName.indexOf(".java");
            if (classNameEnd > 0) {
              String className = fileName.substring(0, classNameEnd);
              if (packageName.endsWith("." + className)) {
                packageName =
                    packageName.substring(0, packageName.length() - className.length() - 1);
              }
              sourceFile = resolveSourceFile(packageName, fileName);
            }
          }
        }
      }
    }

    String stackTrace =
        compressStackFrames
            ? StackTraceCompressor.compress(message, null, includePackages)
            : message;

    String resolvedFile = sourceFile;
    if (resolvedFile == null) {
      Path fileName = file.getFileName();
      resolvedFile = fileName != null ? fileName.toString() : "unknown";
    }

    return new BuildError(
        type, resolvedFile, line, extractFirstLine(message), stackTrace, duration, testLogs);
  }

  private static boolean isFrameworkFrame(String line, List<String> includePackages) {
    String trimmed = line.trim();
    if (!trimmed.startsWith("at ")) {
      return false;
    }

    // Explicit inclusion overrides framework detection
    if (includePackages != null) {
      for (String pkg : includePackages) {
        if (trimmed.contains("at " + pkg)) {
          return false; // It's not a framework frame (we want to keep it)
        }
      }
    }

    String[] frameworkPrefixes = {
      "org.junit.",
      "org.opentest4j.",
      "java.",
      "javax.",
      "sun.",
      "com.sun.",
      "jdk.",
      "org.apache.maven.",
      "org.gradle.",
      "org.springframework.",
      "org.hibernate.",
      "io.projectreactor.",
      "reactor.core.",
      "io.micronaut.",
      "io.netty."
    };

    for (String prefix : frameworkPrefixes) {
      if (trimmed.contains("at " + prefix)) {
        return true;
      }
    }
    return false;
  }

  private SurefireParser() {}
}
