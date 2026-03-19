# LLM Build Compactor

A universal, zero-config tool that extracts **actionable build diagnostics** from Maven and Gradle runs. It filters out thousands of lines of framework noise (JUnit, Spring, Micronaut, Reactor) and produces a high-signal JSON or human-readable summary of failures optimized for AI agents and build-repair loops.

## Features

- **High-Signal Output**: Suppresses most build noise and emits a compact final summary instead of raw build logs.
- **Smart Compaction**: Filters stack traces to show only your project's code.
- **Agent-First Output**: Pretty-printed JSON or clean human-readable text.
- **Contextual Fixes**: Identifies the exact file/line and provides 7-line code snippets for errors.
- **Git Integration**: Includes a list of recently changed files to provide context.
- **Test Insights**: Optional test duration tracking and percentile reports (p50-max).

---

## Installation (Maven)

The easiest way to set up the compactor is using the `install` goal:

```bash
mvn io.llmcompactor:llm-compactor-maven-plugin:0.1.3:install
```

This automatically creates `.mvn/extensions.xml` in your project, enabling the Core Extension for absolute silence.

---

## Installation (Gradle)

### 1. Apply the Plugin

Add the plugin to your `build.gradle.kts` (Kotlin DSL):

```kotlin
plugins {
    id("io.llmcompactor.gradle") version "0.1.3"
}
```

Or `build.gradle` (Groovy DSL):

```groovy
plugins {
    id 'io.llmcompactor.gradle' version '0.1.3'
}
```

### 2. Configure the Plugin

**Kotlin DSL** (`build.gradle.kts`):
```kotlin
llmCompactor {
    enabled.set(true)
    outputAsJson.set(false)
    compressStackFrames.set(true)
    showFixTargets.set(true)
    showRecentChanges.set(true)
    showDuration.set(true)
}
```

**Groovy DSL** (`build.gradle`):
```groovy
llmCompactor {
    enabled = true
    outputAsJson = false
    compressStackFrames = true
    showFixTargets = true
    showRecentChanges = true
    showDuration = true
}
```

### 3. Install the Bootstrap Files

To enable the Gradle bootstrap path, install the companion init script:

```bash
./gradlew installLlmCompactor
```

This installs:
- a companion init script in `~/.gradle/init.d/`

The plugin then applies task-level suppression and emits a single final summary for the build.

---

## Configuration

Settings can be configured in your `pom.xml` (Maven), `build.gradle` (Gradle), or passed as system properties.

### Available Options

| Property | Default | Description |
|----------|---------|-------------|
| `llmCompactor.enabled` | `true` | Toggle the entire tool on/off. |
| `llmCompactor.outputAsJson` | `true` | Emit pretty-printed JSON. If `false`, emits human-readable text. |
| `llmCompactor.compressStackFrames` | `true` | Filter out framework noise from stack traces. |
| `llmCompactor.showFixTargets` | `true` | Include suggested files and snippets for fixing errors. |
| `llmCompactor.showRecentChanges` | `true` | Include the list of files changed in git recently. |
| `llmCompactor.includePackages` | (empty) | Comma-separated list of packages to *always* include in stack traces. **Note:** The compactor automatically scans your `src/main`, `src/test`, and `src/it` to identify and preserve project-specific packages. |
| `llmCompactor.showDuration` | `true` | Show duration for individual failing tests. |
| `llmCompactor.showTotalDuration` | `false` | Include the total build execution time in the summary. |
| `llmCompactor.showDurationReport` | `false` | Include a heuristic percentile report of test durations (p50, p90, p95, p99, max). |
| `llmCompactor.outputPath` | (varies) | Path where the summary is saved (e.g., `target/llm-summary.json`). |

---

## Disabling the Compactor

If you need a standard build with full output (e.g., for debugging or when a build hangs), disable it via the command line:

```bash
mvn verify -DllmCompactor.enabled=false
# OR
./gradlew build -DllmCompactor.enabled=false
```

---

## Fix Targets & Code Snippets

For every build error, the compactor identifies the source file (checking `src/main`, `src/test`, and `src/it`) and provides a **7-line code snippet** (3 lines before/after the failure).

This provides **instant context** for AI agents, making fixes cheaper and faster by eliminating extra `read_file` calls.

### Example in JSON:
```json
"fixTargets": [
  {
    "file": "src/it/java/io/llmcompactor/testbed/OrderProcessorIT.java",
    "line": 49,
    "reason": "org.opentest4j.AssertionFailedError: expected: <100.00> but was: <175.00>",
    "snippet": "        BigDecimal total = processor.calculateTotal(orders);\n        \n        // Intentional failure - wrong expected total\n        assertEquals(new BigDecimal(\"100.00\"), total, \n            \"Total should be sum of all orders\");\n    }\n"
  }
]
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
  ],
  "testDurationPercentiles": {
    "p50": 12.5,
    "p95": 145.2,
    "max": 512.0
  }
}
```

### Human-Readable
```text
=== LLM Build Compactor Summary ===
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
