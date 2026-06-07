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

  static void collectDurationsAndSlowTests(
      Document doc, List<Double> allDurations, List<SlowTest> slowTests) {
    NodeList testCaseNodes = doc.getElementsByTagName("testcase");
    for (int i = 0; i < testCaseNodes.getLength(); i++) {
      Element testCase = (Element) testCaseNodes.item(i);
      String timeAttr = testCase.getAttribute("time");
      double durationMs = 0.0;
      if (timeAttr != null && !timeAttr.isEmpty()) {
        try {
          durationMs = Double.parseDouble(timeAttr) * 1000;
          allDurations.add(durationMs);
        } catch (NumberFormatException e) {
          // Ignore
        }
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
