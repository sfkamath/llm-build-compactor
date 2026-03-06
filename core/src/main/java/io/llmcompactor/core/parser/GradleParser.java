package io.llmcompactor.core.parser;

import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.StackTraceCompressor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Parses Gradle test result XML files (typically in build/test-results/test/*.xml). */
public final class GradleParser {

  private static final Pattern LINE_NUMBER_PATTERN = Pattern.compile("\\.java:(\\d+)");

  public static TestResult parse(
      Path testResultsDir, boolean compressStackFrames, List<String> includePackages) {
    if (!Files.exists(testResultsDir)) {
      return new TestResult(0, 0, Collections.emptyList());
    }

    List<BuildError> failures = new ArrayList<>();
    AtomicInteger totalTests = new AtomicInteger(0);
    AtomicInteger testFailures = new AtomicInteger(0);

    try (Stream<Path> files = Files.walk(testResultsDir)) {
      files
          .filter(f -> f.toString().endsWith(".xml"))
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

                  NodeList failureNodes = doc.getElementsByTagName("failure");
                  for (int i = 0; i < failureNodes.getLength(); i++) {
                    var node = failureNodes.item(i);
                    String message = node.getTextContent().trim();
                    String type = ((Element) node).getAttribute("type");

                    // In Gradle, the test case name and class are in the parent element
                    Element testCase = (Element) node.getParentNode();
                    String className = testCase.getAttribute("classname");

                    String sourceFile = null;
                    int line = -1;

                    // Try to find the first project frame
                    String[] lines = message.split("\n");
                    for (String l : lines) {
                      if (l.contains(".java:") && isProjectFrame(l, className)) {
                        Matcher m = LINE_NUMBER_PATTERN.matcher(l);
                        if (m.find()) {
                          line = Integer.parseInt(m.group(1));
                          sourceFile = "src/test/java/" + className.replace(".", "/") + ".java";
                          break;
                        }
                      }
                    }

                    String stackTrace =
                        compressStackFrames
                            ? StackTraceCompressor.compress(message, null, includePackages)
                            : message;

                    failures.add(
                        new BuildError(
                            type,
                            sourceFile != null ? sourceFile : className,
                            line,
                            extractFirstLine(message),
                            stackTrace));
                    testFailures.incrementAndGet();
                  }

                } catch (ParserConfigurationException
                    | SAXException
                    | IOException e) {
                  // Ignore corrupt XML
                }
              });
    } catch (IOException e) {
      // Ignore IO errors
    }

    return new TestResult(totalTests.get(), testFailures.get(), failures);
  }

  private static boolean isProjectFrame(String line, String className) {
    return line.contains(className);
  }

  private static String extractFirstLine(String message) {
    if (message == null || message.isEmpty()) {
      return "";
    }
    return message.split("\n")[0].trim();
  }

  private GradleParser() {}
}
