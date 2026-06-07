package io.llmcompactor.core.parser;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Utility for reading system-out and system-err logs from JUnit XML testcase elements. */
public final class TestLogReader {

  /**
   * Reads test logs from a testcase element, optionally falling back to its parent (suite) level.
   *
   * @param testCaseElement the <testcase> element to read logs from
   * @param fallbackToSuite if true, looks at the parent element if no logs are found in the
   *     testcase
   * @param labelFormat a format string for labeling log sections (e.g., "[%s]" or "[%s for
   *     %s#%s]"). Supports %s for stream name (system-out/err), and optionally %s#%s for
   *     className#testName.
   * @return the combined logs, or null if none found
   */
  public static String read(Element testCaseElement, boolean fallbackToSuite, String labelFormat) {
    String testName = testCaseElement.getAttribute("name");
    String className = testCaseElement.getAttribute("classname");

    StringBuilder logs = new StringBuilder();
    appendOutputNodes(testCaseElement.getChildNodes(), logs, labelFormat, className, testName);

    // Fall back to suite output when no testcase-level output is present (typical for Gradle)
    if (fallbackToSuite && logs.length() == 0) {
      Node parent = testCaseElement.getParentNode();
      if (parent instanceof Element) {
        appendOutputNodes(parent.getChildNodes(), logs, labelFormat, className, testName);
      }
    }

    return logs.length() > 0 ? logs.toString() : null;
  }

  private static void appendOutputNodes(
      NodeList nodes, StringBuilder logs, String labelFormat, String className, String testName) {
    for (int i = 0; i < nodes.getLength(); i++) {
      Node child = nodes.item(i);
      String nodeName = child.getNodeName();
      if ("system-out".equals(nodeName) || "system-err".equals(nodeName)) {
        String content = child.getTextContent();
        if (content != null && !content.trim().isEmpty()) {
          if (logs.length() > 0) {
            logs.append("\n");
          }

          String label;
          if (labelFormat.contains("%s#%s") || labelFormat.split("%s").length > 2) {
            // Label with stream name + class + method
            label = String.format(labelFormat, nodeName, className, testName);
          } else {
            // Simple label with just stream name
            label = String.format(labelFormat, nodeName);
          }

          logs.append(label).append("\n").append(content);
        }
      }
    }
  }

  private TestLogReader() {}
}
