# LLM Build Compactor

A lightweight tool that extracts **actionable build diagnostics** from Maven runs. It filters out thousands of lines of framework noise (JUnit, Spring, Micronaut, Reactor) and produces a high-signal JSON or human-readable summary of failures.

This dramatically reduces context usage and improves the success rate for AI coding agents (and humans) fixing build errors.

## Features

- **Absolute Silence**: Suppresses all Maven/SLF4J banners and logs using a Core Extension.
- **Smart Compaction**: Filters stack traces to show only your project's code.
- **Agent-First Output**: Pretty-printed JSON or clean human-readable text.
- **Contextual Fixes**: Identifies the exact file/line and provides code snippets for errors.
- **Git Integration**: Includes a list of recently changed files to provide context.

---

## Installation (Maven)

The easiest way to set up the compactor is using the `install` goal:

```bash
mvn io.llmcompactor:llm-compactor-maven-plugin:0.1.0:install
```

This automatically creates `.mvn/extensions.xml` in your project, enabling the Core Extension for absolute silence.

---

## Configuration

Settings can be configured in your `pom.xml` (via `<properties>` or plugin `<configuration>`) or passed as system properties on the command line.

### Available Options

| Property | Default | Description |
|----------|---------|-------------|
| `llmCompactor.enabled` | `true` | Toggle the entire tool on/off. |
| `llmCompactor.outputAsJson` | `true` | Emit pretty-printed JSON. If `false`, emits human-readable text. |
| `llmCompactor.compressStackFrames` | `true` | Filter out framework noise from stack traces. |
| `llmCompactor.showFixTargets` | `true` | Include suggested files and snippets for fixing errors. |
| `llmCompactor.showRecentChanges` | `true` | Include the list of files changed in git recently. |
| `llmCompactor.includePackages` | (empty) | Comma-separated list of packages to *always* include in stack traces (overrides filtering). |
| `llmCompactor.outputPath` | `target/llm-summary.json` | Path where the summary is saved. |

---

## Usage Examples

### 1. High-Signal Silent Build (Standard for AI Agents)
```bash
mvn verify -DllmCompactor.outputAsJson=true
```

### 2. Human-Readable Debugging
```bash
mvn test -DllmCompactor.outputAsJson=false
```

### 3. Including External Library Context
If you are debugging an issue in a library like `com.example.lib`, you can force its frames to show up:
```bash
mvn verify -DllmCompactor.includePackages=com.example.lib
```

### 4. POM Configuration
```xml
<properties>
    <llmCompactor.outputAsJson>false</llmCompactor.outputAsJson>
    <llmCompactor.showFixTargets>false</llmCompactor.showFixTargets>
</properties>

<!-- OR within the plugin declaration -->
<plugin>
    <groupId>io.llmcompactor</groupId>
    <artifactId>llm-compactor-maven-plugin</artifactId>
    <version>0.1.0</version>
    <configuration>
        <outputAsJson>true</outputAsJson>
        <compressStackFrames>true</compressStackFrames>
    </configuration>
</plugin>
```

---

## Output Formats

### JSON (Default)
```json
{
  "status": "FAILED",
  "testsRun": 18,
  "failures": 9,
  "errors": [
    {
      "type": "java.lang.RuntimeException",
      "file": "src/test/java/io/llmcompactor/testbed/StackTraceTest.java",
      "line": 37,
      "message": "Order processing failed",
      "stackTrace": "at io.llmcompactor.testbed.StackTraceTest.testNestedException(StackTraceTest.java:37)\nCaused by: java.lang.IllegalArgumentException: Order validation failed\nat io.llmcompactor.testbed.StackTraceTest.validateOrder(StackTraceTest.java:47)"
    }
  ]
}
```

### Human-Readable
```text
=== LLM Build Summary ===
Status: FAILED
Tests Run: 18
Failures: 9

Errors:
  - java.lang.RuntimeException at src/test/java/io/llmcompactor/testbed/StackTraceTest.java:37
    Order processing failed
    Stack trace:
        at io.llmcompactor.testbed.StackTraceTest.testNestedException(StackTraceTest.java:37)
      Caused by: java.lang.IllegalArgumentException: Order validation failed
        at io.llmcompactor.testbed.StackTraceTest.validateOrder(StackTraceTest.java:47)
```
