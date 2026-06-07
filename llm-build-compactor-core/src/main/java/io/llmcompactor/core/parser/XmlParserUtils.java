package io.llmcompactor.core.parser;

import io.llmcompactor.core.SlowTest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

final class XmlParserUtils {

  static Document parseDocument(Path file)
      throws ParserConfigurationException, SAXException, IOException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    return builder.parse(file.toFile());
  }

  static int extractTestCount(Document doc) {
    String tests = doc.getDocumentElement().getAttribute("tests");
    if (tests != null && !tests.isEmpty()) {
      return Integer.parseInt(tests);
    }
    return 0;
  }

  /** Parses the 'time' attribute from a JUnit XML element (in seconds) to milliseconds. */
  static double parseDurationSecToMs(Element element) {
    String timeAttr = element.getAttribute("time");
    if (timeAttr == null || timeAttr.isEmpty()) {
      return 0.0;
    }
    try {
      return Double.parseDouble(timeAttr) * 1000;
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }

  static void collectDurationsAndSlowTests(
      Document doc, List<Double> allDurations, List<SlowTest> slowTests) {
    NodeList testCaseNodes = doc.getElementsByTagName("testcase");
    for (int i = 0; i < testCaseNodes.getLength(); i++) {
      Element testCase = (Element) testCaseNodes.item(i);
      double durationMs = parseDurationSecToMs(testCase);
      if (durationMs > 0) {
        allDurations.add(durationMs);
      }
      if (durationMs > 0
          && testCase.getElementsByTagName("failure").getLength() == 0
          && testCase.getElementsByTagName("error").getLength() == 0
          && testCase.getElementsByTagName("skipped").getLength() == 0) {
        String className = testCase.getAttribute("classname");
        String name = testCase.getAttribute("name");
        slowTests.add(new SlowTest(className, name, durationMs));
      }
    }
  }

  private XmlParserUtils() {}
}
