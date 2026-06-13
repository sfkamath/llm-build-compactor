package io.llmcompactor.core.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class XmlParserUtilsTest {

  private DocumentBuilder builder;

  @BeforeEach
  void setUp() throws Exception {
    builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
  }

  @Test
  void extractTestCountReturnsZeroWhenTestsAttributeIsMissing() {
    Document doc = builder.newDocument();
    Element root = doc.createElement("testsuite");
    doc.appendChild(root);

    assertThat(XmlParserUtils.extractTestCount(doc)).isEqualTo(0);
  }

  @Test
  void parseDurationSecToMsReturnsZeroForMissingTime() {
    Document doc = builder.newDocument();
    Element elem = doc.createElement("testcase");

    assertThat(XmlParserUtils.parseDurationSecToMs(elem)).isEqualTo(0.0);
  }

  @Test
  void parseDurationSecToMsReturnsZeroForInvalidTime() {
    Document doc = builder.newDocument();
    Element elem = doc.createElement("testcase");
    elem.setAttribute("time", "not-a-number");

    assertThat(XmlParserUtils.parseDurationSecToMs(elem)).isEqualTo(0.0);
  }

  @Test
  void parseDurationSecToMsConvertsSecondsToMs() throws Exception {
    String xml =
        "<?xml version=\"1.0\"?>"
            + "<testsuite tests=\"1\">"
            + "  <testcase classname=\"Test\" name=\"test\" time=\"2.5\"/>"
            + "</testsuite>";
    Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    Element testCase = (Element) doc.getElementsByTagName("testcase").item(0);

    assertThat(XmlParserUtils.parseDurationSecToMs(testCase)).isEqualTo(2500.0);
  }
}
