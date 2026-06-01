package io.llmcompactor.core.parser;

import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.StackTraceCompressor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
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

/** Parses Gradle test result XML files (typically in build/test-results/test/*.xml). */
public final class GradleParser {

  private static final Pattern LINE_NUMBER_PATTERN = Pattern.compile("\\.java:(\\d+)");
  private static final Pattern GROOVY_LINE_NUMBER_PATTERN = Pattern.compile("\\.groovy:(\\d+)");

  public static TestResult parse(
      Path testResultsDir,
      boolean compressStackFrames,
      List<String> stackFrameWhitelist,
      List<String> stackFrameBlacklist,
      long sessionStartTime,
      boolean showFailedTestLogs) {
    if (!Files.exists(testResultsDir)) {
      return new TestResult(0, 0, Collections.emptyList());
    }

    List<BuildError> failures = new ArrayList<>();
    List<Double> allDurations = new ArrayList<>();
    AtomicInteger totalTests = new AtomicInteger(0);
    AtomicInteger testFailures = new AtomicInteger(0);

    try (Stream<Path> files = Files.walk(testResultsDir)) {
      files
          .filter(f -> f.toString().endsWith(".xml"))
          .filter(p -> p.toFile().lastModified() >= sessionStartTime)
          .forEach(
              file -> {
                try {
                  DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                  DocumentBuilder builder = factory.newDocumentBuilder();
                  Document doc = builder.parse(file.toFile());

                  String tests = doc.getDocumentElement().getAttribute("tests");
                  if (!tests.isEmpty()) {
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
                    String message = node.getTextContent().trim();
                    String type = ((Element) node).getAttribute("type");

                    // In Gradle, the test case name and class are in the parent element
                    Element testCase = (Element) node.getParentNode();
                    String className = testCase.getAttribute("classname");
                    String timeAttr = testCase.getAttribute("time");
                    double duration = 0.0;
                    if (timeAttr != null && !timeAttr.isEmpty()) {
                      try {
                        duration = Double.parseDouble(timeAttr);
                      } catch (NumberFormatException e) {
                        // Ignore
                      }
                    }

                    String testLogs = null;
                    if (showFailedTestLogs) {
                      testLogs = readTestLogs(testCase);
                    }

                    String sourceFile = null;
                    int line = -1;

                    // Extract filename and line from stack frames
                    // Just find the last occurrence of "File.ext:LineNum)" at end of line
                    String[] lines = message.split("\n");
                    String testPackage = className.substring(0, Math.max(0, className.lastIndexOf(".")));

                    for (String l : lines) {
                      if ((l.contains(".java:") || l.contains(".groovy:"))) {
                        // Skip framework frames, but accept frames from test's own package
                        boolean isFramework = StackTraceCompressor.isFrameworkFrame(l);
                        boolean isFromTestPackage = l.contains(testPackage);

                        if (!isFramework || isFromTestPackage) {
                          // Find last colon and closing paren: "File.ext:123)"
                          int lastColon = l.lastIndexOf(":");
                          int lastParen = l.lastIndexOf(")");

                          if (lastColon > 0 && lastParen > lastColon) {
                            try {
                              line = Integer.parseInt(l.substring(lastColon + 1, lastParen));
                              // Find the opening paren before the colon
                              int openParen = l.lastIndexOf("(", lastColon);
                              if (openParen > 0) {
                                sourceFile = l.substring(openParen + 1, lastColon);
                                // Prefer test class frame, use first available frame
                                if (l.contains(className)) {
                                  break;
                                }
                              }
                            } catch (NumberFormatException e) {
                              // Skip if line number is invalid
                            }
                          }
                        }
                      }
                    }

                    String stackTrace =
                        compressStackFrames
                            ? StackTraceCompressor.compress(
                                message, null, stackFrameWhitelist, stackFrameBlacklist)
                            : message;

                    failures.add(
                        new BuildError(
                            type,
                            sourceFile != null ? sourceFile : className,
                            line,
                            ParserUtils.extractFirstLine(message),
                            stackTrace,
                            duration,
                            testLogs));
                    testFailures.incrementAndGet();
                  }

                } catch (ParserConfigurationException | SAXException | IOException e) {
                  // Ignore corrupt XML
                }
              });
    } catch (IOException e) {
      // Ignore IO errors
    }

    return new TestResult(totalTests.get(), testFailures.get(), failures, allDurations);
  }

  private static String readTestLogs(Element testCase) {
    StringBuilder logs = new StringBuilder();

    // First check for system-out/system-err as direct children of testcase
    NodeList children = testCase.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if ("system-out".equals(child.getNodeName()) || "system-err".equals(child.getNodeName())) {
        String content = child.getTextContent();
        if (content != null && !content.trim().isEmpty()) {
          if (logs.length() > 0) {
            logs.append("\n");
          }
          logs.append("[").append(child.getNodeName()).append("]\n").append(content);
        }
      }
    }

    // Gradle places system-out/system-err at the suite level, not per testcase.
    // Fall back to suite output when no testcase-level output is present.
    if (logs.length() == 0) {
      Node parent = testCase.getParentNode();
      if (parent instanceof Element) {
        Element testsuite = (Element) parent;
        NodeList suiteChildren = testsuite.getChildNodes();
        for (int i = 0; i < suiteChildren.getLength(); i++) {
          Node child = suiteChildren.item(i);
          if ("system-out".equals(child.getNodeName())
              || "system-err".equals(child.getNodeName())) {
            String content = child.getTextContent();
            if (content != null && !content.trim().isEmpty()) {
              if (logs.length() > 0) {
                logs.append("\n");
              }
              logs.append("[").append(child.getNodeName()).append("]\n").append(content);
            }
          }
        }
      }
    }

    return logs.length() > 0 ? logs.toString() : null;
  }


  private GradleParser() {}
}
