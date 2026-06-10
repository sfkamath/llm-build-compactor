package io.llmcompactor.core.parser;

import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.SlowTest;
import io.llmcompactor.core.StackTraceCompressor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.ParserConfigurationException;
import lombok.experimental.UtilityClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@UtilityClass
public class SurefireParser {

  private static final Pattern LINE_NUMBER_PATTERN = Pattern.compile("\\.java:(\\d+)\\)");
  private static final Pattern GROOVY_LINE_NUMBER_PATTERN = Pattern.compile("\\.groovy:(\\d+)\\)");

  public static TestResult parse(
      Path targetDir,
      boolean compressStackFrames,
      List<String> stackFrameWhitelist,
      List<String> stackFrameBlacklist,
      long sessionStartTime,
      boolean showFailedTestLogs) {
    List<BuildError> failures = new ArrayList<>();
    List<Double> allDurations = new ArrayList<>();
    List<SlowTest> slowTests = new ArrayList<>();
    AtomicInteger totalTests = new AtomicInteger(0);
    AtomicInteger testFailures = new AtomicInteger(0);

    List<Path> reportDirs =
        Arrays.asList(targetDir.resolve("surefire-reports"), targetDir.resolve("failsafe-reports"));

    for (Path reportsDir : reportDirs) {
      if (Files.exists(reportsDir)) {
        try (Stream<Path> files = Files.list(reportsDir)) {
          files
              .filter(p -> p.toString().endsWith(".xml"))
              .filter(p -> p.toFile().lastModified() >= sessionStartTime - 10000)
              .forEach(
                  file -> {
                    try {
                      Document doc = XmlParserUtils.parseDocument(file);
                      totalTests.addAndGet(XmlParserUtils.extractTestCount(doc));
                      XmlParserUtils.collectDurationsAndSlowTests(doc, allDurations, slowTests);

                      NodeList failureNodes = doc.getElementsByTagName("failure");
                      for (int i = 0; i < failureNodes.getLength(); i++) {
                        Node node = failureNodes.item(i);
                        String type = ((Element) node).getAttribute("type");
                        String message = node.getTextContent().trim();

                        double duration = getTestDuration(node);
                        String testLogs = showFailedTestLogs ? readTestLogs(node) : null;
                        BuildError error =
                            parseError(
                                message,
                                type,
                                file,
                                stackFrameWhitelist,
                                stackFrameBlacklist,
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
                        String testLogs = showFailedTestLogs ? readTestLogs(node) : null;
                        BuildError error =
                            parseError(
                                message,
                                type,
                                file,
                                stackFrameWhitelist,
                                stackFrameBlacklist,
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

    return new TestResult(totalTests.get(), testFailures.get(), failures, allDurations, slowTests);
  }

  private static double getTestDuration(Node node) {
    Node parentNode = node.getParentNode();
    if (parentNode instanceof Element) {
      return XmlParserUtils.parseDurationSecToMs((Element) parentNode);
    }
    return 0.0;
  }

  private static String readTestLogs(Node failureOrError) {
    Node testCase = failureOrError.getParentNode();
    while (testCase instanceof Element && !"testcase".equals(((Element) testCase).getTagName())) {
      testCase = testCase.getParentNode();
    }
    if (!(testCase instanceof Element)) {
      return null;
    }
    return TestLogReader.read((Element) testCase, false, "[%s for %s#%s]");
  }

  private static BuildError parseError(
      String message,
      String type,
      Path file,
      List<String> stackFrameWhitelist,
      List<String> stackFrameBlacklist,
      boolean compressStackFrames,
      double duration,
      String testLogs) {
    String sourceFile = null;
    int line = -1;

    // For file and line detection: use the FIRST project frame (where the error originated)
    // Not the last frame which could be a wrapper/extension class
    // Supports both Java (.java:) and Groovy (.groovy:) files
    String[] lines = message.split("\n");
    String firstProjectFrame = null;
    for (String l : lines) {
      boolean hasJavaFile = l.contains(".java:");
      boolean hasGroovyFile = l.contains(".groovy:");
      if ((hasJavaFile || hasGroovyFile)
          && !StackTraceCompressor.isFrameworkFrame(l, stackFrameWhitelist, stackFrameBlacklist)) {
        if (firstProjectFrame == null) {
          firstProjectFrame = l;
        }
      }
    }

    // Use first frame for both line number AND file detection (where error originated)
    if (firstProjectFrame != null) {
      Matcher m = LINE_NUMBER_PATTERN.matcher(firstProjectFrame);
      boolean found = m.find();
      if (!found) {
        m = GROOVY_LINE_NUMBER_PATTERN.matcher(firstProjectFrame);
        found = m.find();
      }
      if (found) {
        line = Integer.parseInt(m.group(1));
      }

      sourceFile = ParserUtils.resolveFrameSource(firstProjectFrame);
    }

    String stackTrace =
        compressStackFrames
            ? StackTraceCompressor.compress(message, null, stackFrameWhitelist, stackFrameBlacklist)
            : message;

    String resolvedFile = sourceFile;
    if (resolvedFile == null) {
      Path fileName = file.getFileName();
      resolvedFile = fileName != null ? fileName.toString() : "unknown";
    }

    return new BuildError(
        type,
        resolvedFile,
        line,
        ParserUtils.extractFirstLine(message),
        stackTrace,
        duration,
        testLogs);
  }
}
