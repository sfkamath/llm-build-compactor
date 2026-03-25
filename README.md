# LLM Build Compactor

[![Java CI](https://github.com/sfkamath/llm-build-compactor/actions/workflows/ci.yml/badge.svg)](https://github.com/sfkamath/llm-build-compactor/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.sfkamath/llm-build-compactor-extension)](https://central.sonatype.com/artifact/io.github.sfkamath/llm-build-compactor-extension)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.sfkamath.llm-build-compactor)](https://plugins.gradle.org/plugin/io.github.sfkamath.llm-build-compactor)
[![Version](https://img.shields.io/github/v/tag/sfkamath/llm-build-compactor)](https://github.com/sfkamath/llm-build-compactor/tags)
[![License](https://img.shields.io/github/license/sfkamath/llm-build-compactor)](LICENSE)

> **Version note:** Version numbers in code examples below may lag behind the latest release. Always use the version shown in the Maven Central or Gradle Plugin Portal badge above.

A universal, zero-config tool that extracts **actionable build diagnostics** from Maven and Gradle runs. It filters out thousands of lines of framework noise (JUnit, Spring, Micronaut, Reactor) and produces a high-signal JSON or human-readable summary of failures optimized for AI agents and build-repair loops.

## Features

- **High-Signal Output**: Suppresses build noise and emits a compact final summary instead of raw build logs (~60-70% token reduction for typical failures).
- **Smart Compaction**: Filters stack traces to show only your project's frames, and strips package prefixes for brevity.
- **Agent-First Output**: JSON by default (pretty-printed); human-readable text via `outputAsJson=false`.
- **Mode Presets**: One-flag configuration via `mode=agent`, `mode=debug`, or `mode=human` — sets output format, fix targets, and log capture in one shot.
- **Contextual Fixes**: Identifies the exact file/line for every error. For non-test source files, includes a 7-line code snippet around the failure.
- **Test Log Capture**: Optionally captures stdout/stderr and SLF4J/Log4j2 logs for failed tests (`showFailedTestLogs=true`), with noise filtering (timestamps, thread info, log levels stripped).
- **Test Insights**: Optional slow-test highlighting and percentile duration reports (p50–max).
- **Git Integration**: Optionally includes recently changed files for context (`showRecentChanges=true`).

---

## Installation (Maven)

### Step 1: Install the Extension (Required for Complete Silence)

The compactor uses a Maven Extension to achieve complete build silence. Install it using:

```bash
mvn io.github.sfkamath:llm-build-compactor-maven-plugin:0.2.3:install
```

This creates `.mvn/extensions.xml` in your project, enabling the Core Extension that suppresses all build output during execution.

### Step 2: Configure the Plugin (Optional)

To customize the output format and features, add the plugin configuration to your `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>io.github.sfkamath</groupId>
            <artifactId>llm-build-compactor-maven-plugin</artifactId>
            <version>0.2.3</version>
            <configuration>
                <outputAsJson>false</outputAsJson>
                <compressStackFrames>true</compressStackFrames>
                <showFixTargets>false</showFixTargets>
                <showRecentChanges>false</showRecentChanges>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>compact</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

**Note:** Without the extension (Step 1), you will not get complete silence during builds.

---

## Installation (Gradle)

### 1. Apply the Plugin

Add the plugin to your `build.gradle.kts` (Kotlin DSL):

```kotlin
plugins {
    id("io.github.sfkamath.llm-build-compactor") version "0.2.3"
}
```

Or `build.gradle` (Groovy DSL):

```groovy
plugins {
    id 'io.github.sfkamath.llm-build-compactor' version '0.2.3'
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
    showSlowTests.set(true)
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
    showSlowTests = true
}
```

### 3. Global Settings (Auto-Installed)

When you first apply the plugin, it automatically installs two global suppressions:

| File | What it adds |
|------|-------------|
| `~/.gradle/init.d/llm-compactor-silence.gradle` | Init script that suppresses Gradle lifecycle output during the initialization phase, before plugin code runs |
| `~/.gradle/gradle.properties` | `org.gradle.logging.level=quiet` wrapped in `# >>> llm-compactor >>>` markers, which silences per-test pass/fail lines (e.g. Spock's `MySpec some test PASSED`) |

Both are managed as a unit by `installLlmCompactor` / `uninstallLlmCompactor`.

> **Warning:** These settings affect **all Gradle builds on your machine**, not just
> projects that use this plugin. A one-line notice is printed the first time they are installed.

#### Uninstalling

If you want to remove both suppressions (e.g. when removing the plugin from your project):

```bash
./gradlew uninstallLlmCompactor
./gradlew --stop
```

The `./gradlew --stop` is **required**. The running Gradle daemon loaded the init script at
startup and will continue suppressing output for all builds until it is restarted.

> **If you remove the plugin from your build file without running `uninstallLlmCompactor`
> first**, both suppressions remain active. Run `./gradlew uninstallLlmCompactor && ./gradlew --stop`
> from any project that still has the plugin applied, or remove them manually:
> ```
> rm ~/.gradle/init.d/llm-compactor-silence.gradle
> # remove the llm-compactor block from ~/.gradle/gradle.properties
> ```

#### Re-installing manually

If the settings are ever lost (e.g. after cleaning `~/.gradle`), reinstall them with:

```bash
./gradlew installLlmCompactor
```

---

## Configuration

Settings can be configured in your `pom.xml` (Maven), `build.gradle` (Gradle), or passed as system properties.

### Available Options

**Note:** Defaults are centralized in [`CompactorDefaults.java`](llm-build-compactor-core/src/main/java/io/llmcompactor/core/CompactorDefaults.java) and referenced by all build tools.

| Property                               | Default  | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
|----------------------------------------|----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `llmCompactor.enabled`                 | `true`   | Toggle the entire tool on/off.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| `llmCompactor.mode`                    | (none)   | Output mode preset. When set, **completely overrides** `outputAsJson`, `showFixTargets`, and `showFailedTestLogs`. See [Output Modes](#output-modes) below.                                                                                                                                                                                                                                                                                                                                                                                                              |
| `llmCompactor.outputAsJson`            | `true`   | Emit pretty-printed JSON. If `false`, emits human-readable text.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `llmCompactor.showFixTargets`          | `true`   | Include suggested files for fixing errors.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `llmCompactor.showRecentChanges`       | `false`  | Include the list of files changed in git recently.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `llmCompactor.stackFrameWhitelist`     | (empty)  | Comma-separated list of packages to *always* include in stack traces (whitelist). Use this to see framework stack traces like Micronaut or Spring that are normally filtered. The compactor automatically scans your `src/main`, `src/test`, and `src/it` to identify and preserve project-specific packages.                                                                                                                                                                                                                                                            |
| `llmCompactor.stackFrameBlacklist`     | (empty)  | Comma-separated list of packages to *always* exclude from stack traces (blacklist). Use this to filter additional framework packages beyond the defaults, or to exclude specific project packages from stack traces. **Default Framework Exclusions:** The following packages are automatically excluded from stack traces: `java.*`, `javax.*`, `sun.*`, `com.sun.*`, `jdk.*`, `org.junit.*`, `org.testng.*`, `org.apache.maven.*`, `org.gradle.*`, `org.springframework.*`, `org.hibernate.*`, `io.projectreactor.*`, `reactor.core.*`, `io.micronaut.*`, `io.netty.*` |
| `llmCompactor.showSlowTests`           | `true`   | Show test duration only for slow tests (≥ threshold).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `llmCompactor.showTotalDuration`       | `false`  | Include the total build execution time in the summary.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `llmCompactor.showDurationReport`      | `false`  | Include a heuristic percentile report of test durations (p50, p90, p95, p99, max).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `llmCompactor.outputPath`              | (varies) | Path where the summary is saved (e.g., `target/llm-summary.json`).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `llmCompactor.showFailedTestLogs`      | `false`  | Capture and display test output logs for failed tests.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `llmCompactor.testDurationThresholdMs` | `100`    | Threshold in milliseconds for considering a test "slow" (used by `showSlowTests`).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |

### Advanced Options

These options are rarely needed. Use only for specific edge cases.

| Property | Default | Description |
|----------|---------|-------------|
| `llmCompactor.compressStackFrames` | `true` | Filter out framework noise from stack traces. **Recommended:** Keep enabled for agent use. Disable only if you need full raw stack traces for debugging the compactor itself. |

---

## Output Modes

For convenience, you can use the `mode` property to set sensible defaults for common use cases:

### Mode: `agent` (Recommended for AI Agents)
```bash
mvn test -DllmCompactor.mode=agent
```
- JSON output
- Fix targets included
- No test logs (reduces noise)

### Mode: `debug` (For Debugging Test Failures)
```bash
mvn test -DllmCompactor.mode=debug
```
- JSON output
- Fix targets included
- Test logs included (helpful for understanding test failures)

### Mode: `human` (Human-Readable Output)
```bash
mvn test -DllmCompactor.mode=human
```
- Human-readable text output
- Fix targets included
- No test logs

**Precedence:** When `mode` is set, it **completely overrides** `outputAsJson`, `showFixTargets`, and `showFailedTestLogs`. Individual flag values are ignored. For example, `-DllmCompactor.mode=agent -DllmCompactor.showFailedTestLogs=true` will **not** include test logs — the `agent` mode wins.

---

## Capturing Test Logs

When `showFailedTestLogs` is enabled (default: `false`), the compactor captures and displays test output logs for failed tests:

### What Gets Captured

- **System.out.println()** - Standard output from tests
- **System.err.println()** - Standard error from tests
- **SLF4J logs** - Via Logback or Log4j2 bindings
- **Log4j2 logs** - Direct Log4j2 API calls

### Maven Configuration

```xml
<plugin>
    <groupId>io.github.sfkamath</groupId>
    <artifactId>llm-build-compactor-maven-plugin</artifactId>
    <version>0.2.3</version>
    <configuration>
        <showFailedTestLogs>true</showFailedTestLogs>
    </configuration>
</plugin>
```

### Gradle Configuration

```groovy
llmCompactor {
    showFailedTestLogs = true
}
```

### Example Output

```text
Errors:
  - OrderServiceTest.java:41
    Discount calculation failed
        at OrderServiceTest.testOrderWithDiscount(OrderServiceTest.java:41)
    Test logs:
        SLF4J/Logback: Testing discount calculation
        System.out: Applying 10% discount to order ORD-101
```

### Logging Framework Setup

For SLF4J/Logback or Log4j2 logs to appear, ensure you have the appropriate dependencies and configuration:

**Maven (pom.xml):**
```xml
<dependencies>
    <!-- SLF4J with Logback -->
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
        <version>1.4.11</version>
        <scope>test</scope>
    </dependency>
    
    <!-- Or Log4j2 -->
    <dependency>
        <groupId>org.apache.logging.log4j</groupId>
        <artifactId>log4j-api</artifactId>
        <version>2.20.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.apache.logging.log4j</groupId>
        <artifactId>log4j-core</artifactId>
        <version>2.20.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.apache.logging.log4j</groupId>
        <artifactId>log4j-slf4j2-impl</artifactId>
        <version>2.20.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**Test Configuration Files:**
- `src/test/resources/logback-test.xml` for Logback
- `src/test/resources/log4j2-test.xml` for Log4j2

### Disabling Test Logs

To disable test log capture:

```bash
mvn verify -DllmCompactor.showFailedTestLogs=false
# OR
./gradlew test -DllmCompactor.showFailedTestLogs=false
```

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
    "reason": "AssertionFailedError: expected: <100.00> but was: <175.00>"
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
      "stackTrace": "at StackTraceTest.testNestedException(StackTraceTest.java:37)\nCaused by: java.lang.IllegalArgumentException: Order validation failed\nat StackTraceTest.validateOrder(StackTraceTest.java:47)"
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
  - src/test/java/io/llmcompactor/testbed/StackTraceTest.java:37
    Order processing failed
        at StackTraceTest.testNestedException(StackTraceTest.java:37)
        Caused by: java.lang.IllegalArgumentException: Order validation failed
        at StackTraceTest.validateOrder(StackTraceTest.java:47)
```

---

## Development

For information on building, testing, and contributing to the LLM Build Compactor itself, see [`docs/development-guide.md`](docs/development-guide.md).

For a detailed explanation of how the integration tests work — including why they cannot be run in isolation, how the test projects are wired up, and how to debug failures — see [`integration-tests/README.md`](integration-tests/README.md).

**Quick Start:**

```bash
# Build all modules (requires Java 8+)
./mvnw clean install

# Run tests across all Java versions (8, 11, 17, 21, 25)
./scripts/test-quick.sh

# Test with intentional failures (verifies plugin behavior)
cd test-project-maven && mvn clean verify
```

**Java Version Support:** Java 8 through 25. See [`docs/development-guide.md`](docs/development-guide.md) for the Gradle wrapper strategy and multi-version testing.
