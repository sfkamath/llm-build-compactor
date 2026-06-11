package io.llmcompactor.core.parser;

import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.SlowTest;
import io.llmcompactor.core.StackTraceCompressor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.xml.parsers.ParserConfigurationException;
import lombok.experimental.UtilityClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@UtilityClass
public class GradleParser {
  private static final Logger LOGGER = Logger.getLogger(GradleParser.class.getName());

  public static TestResult parse(
      Path testResultsDir,
      boolean compressStackFrames,
      List<String> stackFrameWhitelist,
      List<String> stackFrameBlacklist,
      boolean showFailedTestLogs) {
    return parse(
        testResultsDir,
        compressStackFrames,
        stackFrameWhitelist,
        stackFrameBlacklist,
        showFailedTestLogs,
        0L);
  }

  /**
   * Parses JUnit-style {@code test-results/} XML, optionally ignoring files left over from an
   * earlier build.
   *
   * <p>Gradle does <em>not</em> clear {@code build/test-results/} between unrelated invocations, so
   * an invocation that runs no Test task (a {@code compileJava}-only build, or a build that fails
   * at task selection / configuration) leaves a previous run's XML in place. Parsing it
   * unconditionally makes the compactor replay that stale result as if it belonged to the current
   * build — masking the real outcome, including non-test failures such as a wrong task path or a
   * config error. Field-found on {@code micronaut-data}; gated here via {@code
   * minLastModifiedMillis}.
   *
   * @param minLastModifiedMillis only parse result files modified at or after this epoch-millis
   *     timestamp. Pass {@code 0L} to parse all files. Callers pass the current build's start time
   *     so that a leftover {@code test-results/} dir from a previous run (whose Test task did not
   *     execute this build) is not attributed to this build. Sound because Gradle cleans a Test
   *     task's output dir on execution, so a task that actually ran rewrites all its files fresh
   *     (the only downgrade: an UP-TO-DATE Test task that does not rewrite its dir reports zero —
   *     honest, since nothing ran this build).
   */
  public static TestResult parse(
      Path testResultsDir,
      boolean compressStackFrames,
      List<String> stackFrameWhitelist,
      List<String> stackFrameBlacklist,
      boolean showFailedTestLogs,
      long minLastModifiedMillis) {
    if (!Files.exists(testResultsDir)) {
      return new TestResult(0, 0, Collections.emptyList());
    }

    List<BuildError> failures = new ArrayList<>();
    List<Double> allDurations = new ArrayList<>();
    List<SlowTest> slowTests = new ArrayList<>();
    AtomicInteger totalTests = new AtomicInteger(0);
    AtomicInteger testFailures = new AtomicInteger(0);

    try (Stream<Path> files = Files.walk(testResultsDir)) {
      files
          .filter(f -> f.toString().endsWith(".xml"))
          .filter(f -> f.toFile().lastModified() >= minLastModifiedMillis)
          .forEach(
              file ->
                  parseTestResultFile(
                      file,
                      totalTests,
                      testFailures,
                      allDurations,
                      slowTests,
                      failures,
                      showFailedTestLogs,
                      compressStackFrames,
                      stackFrameWhitelist,
                      stackFrameBlacklist));
    } catch (IOException e) {
      LOGGER.fine("Error reading test results from " + testResultsDir + ": " + e.getMessage());
    }

    return new TestResult(totalTests.get(), testFailures.get(), failures, allDurations, slowTests);
  }

  private static void parseTestResultFile(
      Path file,
      AtomicInteger totalTests,
      AtomicInteger testFailures,
      List<Double> allDurations,
      List<SlowTest> slowTests,
      List<BuildError> failures,
      boolean showFailedTestLogs,
      boolean compressStackFrames,
      List<String> stackFrameWhitelist,
      List<String> stackFrameBlacklist) {
    try {
      Document doc = XmlParserUtils.parseDocument(file);
      totalTests.addAndGet(XmlParserUtils.extractTestCount(doc));
      XmlParserUtils.collectDurationsAndSlowTests(doc, allDurations, slowTests);

      NodeList failureNodes = doc.getElementsByTagName("failure");
      for (int i = 0; i < failureNodes.getLength(); i++) {
        Node node = failureNodes.item(i);
        String message = node.getTextContent().trim();
        String type = ((Element) node).getAttribute("type");

        Element testCase = (Element) node.getParentNode();
        String className = testCase.getAttribute("classname");
        double duration = XmlParserUtils.parseDurationSecToMs(testCase);

        String testLogs = null;
        if (showFailedTestLogs) {
          testLogs = TestLogReader.read(testCase, true, "[%s]");
        }

        String sourceFile = null;
        int line = -1;

        String[] lines = message.split("\n");
        String testPackage = className.substring(0, Math.max(0, className.lastIndexOf(".")));

        for (String l : lines) {
          if (l.contains(".java:") || l.contains(".groovy:")) {
            boolean isFramework = StackTraceCompressor.isFrameworkFrame(l);
            boolean isFromTestPackage = !testPackage.isEmpty() && l.contains(testPackage);

            if (!isFramework || isFromTestPackage) {
              int lastColon = l.lastIndexOf(":");
              int lastParen = l.lastIndexOf(")");

              if (lastColon > 0 && lastParen > lastColon) {
                line = parseCandidateLine(l, lastColon, lastParen, className, line);
                if (line > 0) {
                  int openParen = l.lastIndexOf("(", lastColon);
                  if (openParen > 0) {
                    String resolved = ParserUtils.resolveFrameSource(l);
                    sourceFile =
                        resolved != null ? resolved : l.substring(openParen + 1, lastColon);
                  }
                  if (l.contains(className)) {
                    break;
                  }
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
  }

  private static int parseCandidateLine(
      String l, int lastColon, int lastParen, String className, int currentLine) {
    try {
      int candidateLine = Integer.parseInt(l.substring(lastColon + 1, lastParen));
      int openParen = l.lastIndexOf("(", lastColon);
      if (openParen > 0) {
        if (currentLine == -1) {
          return candidateLine; // first valid frame
        }
        if (l.contains(className)) {
          return candidateLine; // prefer test class frame
        }
      }
    } catch (NumberFormatException ignored) {
    }
    return currentLine;
  }
}
