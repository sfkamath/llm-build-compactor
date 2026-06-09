# Gradle Design

## Introduction

This document describes how the Gradle side of `llm-build-compactor` works today, why it is structured differently from the Maven path, and which parts of the Gradle lifecycle matter for output suppression.

The goal is simple:

- suppress noisy Gradle build output that inflates LLM context
- preserve enough signal to understand failures
- emit one compact final summary at the end of the build

For Gradle, this has to be done with the grain of Gradle's logging system rather than by assuming `System.out` and `System.err` can be globally swapped late in the build.

## Design Constraints

Gradle does not provide the same early lifecycle hooks that Maven provides for this problem.

Maven gives this project a lifecycle extension point via `EventSpy`, which can silence output very early and print a final summary at session end. Gradle does not have a directly equivalent public extension point for full-build output interception.

The important constraints are:

- Gradle configures a large part of its logging pipeline before normal plugin code runs.
- Build output may come from Gradle lifecycle events, task logging, compiler output, test frameworks, Ant-based tools, and worker processes.
- Changing `System.out` or `System.err` inside normal plugin application is not enough to silence everything.
- Some output is already inside Gradle's logging/event pipeline by the time the plugin sees it.

That means the Gradle implementation is necessarily more layered than the Maven extension model.

## Relevant Lifecycle

The current Gradle path uses one stage:

1. the Gradle plugin itself, applied in the target build, for task-level quieting, log capture, test result parsing, and final summary emission

The timing matters:

- Startup logging configuration happens very early in Gradle.
- Init scripts run before project build scripts, but still after some startup logging decisions.
- Plugin `apply()` runs during project configuration.
- `buildFinished` is the reliable point for emitting the final compact summary.

## Current Flow

```mermaid
flowchart TD
    A[gradle invocation] --> B[Gradle startup logging configured]
    B --> C[projects configured]
    C --> D[LlmCompactorPlugin apply]
    D --> E[register root buildFinished listener once]
    D --> F[configure every task to capture stdout/stderr at DEBUG]
    D --> G[quiet JavaCompile tasks]
    D --> H[quiet Test, JavaExec, Checkstyle noise]
    H -.-> H1["register afterSuite TestListener on each Test task<br/><i>(at configuration time)</i>"]
    F --> I[task execution]
    G --> I
    H --> I
    I -.-> H2["afterSuite(root) fires at end of test execution:<br/>reflect into TestCountLogger"]
    H2 --> H3[call completed&#40;&#41; on ProgressLogger]
    H3 --> H4["replace all interface fields<br/>(except Collection/Map) with no-op Proxies"]
    I --> J[test XML reports written under build/test-results]
    I --> K[log lines captured in-memory]
    J --> L[buildFinished]
    K --> L
    L --> M[parse test reports and extract compilation/test errors]
    M --> N[aggregate errors and optional fix targets]
    N --> O[emit one compact final summary]
```

## How Suppression Is Achieved

### 1. Root-Level Plugin Registration

The plugin registers its main listener once at the root build:

- `gradle-plugin/src/main/java/io/llmcompactor/gradle/LlmCompactorPlugin.java`

This avoids duplicate summaries in multi-project builds and ensures that summary generation happens once for the entire build, not once per subproject.

### 3. Task-Level Log Suppression

The most important practical suppression mechanism is task-scoped logging capture.

For enabled builds, each task is configured so its captured stdout and stderr are downgraded to `DEBUG`. Under the quiet logging mode used by the compactor, those lines are then filtered out before they reach the console.

This is what suppresses noisy categories that were otherwise still leaking:

- compiler `Note:` lines
- annotation processor warnings emitted through task output
- Checkstyle chatter
- SLF4J bootstrap warnings
- other task-scoped stdout/stderr noise

This was the key difference between "partially quiet" and "actually quiet in real builds".

### 4. JavaCompile-Specific Quieting

`JavaCompile` tasks also get additional compile options applied:

- warnings disabled
- deprecation output disabled
- specific compiler args added to reduce warning noise

This does not solve the whole problem alone, but it reduces noise at the source before Gradle has to filter it.

### 5. Test Result Parsing Instead of Console Parsing

The Gradle path does not rely only on console text to understand test failures.

Instead, on `buildFinished`, the plugin walks each project's `build/test-results` directory and parses the XML results. This produces:

- total tests run
- failure counts
- exception types
- source files and lines where available
- compressed stack traces
- optional duration data

This is why the compactor can still provide useful failure summaries even when the live console output is heavily suppressed.

### 6. Final Summary Emission

At the end of the build, the plugin:

- aggregates extracted errors
- optionally generates fix targets
- optionally includes recent Git changes
- renders one human-readable or JSON summary

The summary is emitted at build end as the primary user-visible output of the compactor.

## Property Handling

The Gradle plugin reads configuration through Gradle's own `findProperty()` API, which natively resolves `-D` command-line flags, `gradle.properties`, and plugin extension values in a consistent priority order. This is unlike the Maven extension, which must explicitly check `session.getUserProperties()` because Maven `-D` flags are user properties and may not be propagated to JVM system properties.

As a result, the Gradle property path is simpler and has no equivalent to Maven's plugin-XML-overriding-CLI-flag failure mode.

## Why Lifecycle Details Matter

The lifecycle constraints explain several non-obvious design choices:

- The plugin cannot rely purely on `System.setOut()` and `System.setErr()` during `apply()`.
- Some Gradle output is already a Gradle logging event, not raw process text.
- Startup-level Gradle behavior and task-level logging behavior are different problems.
- Suppression that works for one task type may fail for another unless task logging capture is applied broadly.
- End-of-build summary emission must happen once, at root scope, after all test reports exist.

Without understanding those lifecycle boundaries, the implementation looks more complicated than it really is.

## Multi-Project Behavior

The current design is intended to work correctly for multi-project builds:

- the plugin registers summary emission once at the root build
- each subproject's test result directories are scanned
- task-level noise suppression is applied across all projects

That is important because many real-world Gradle builds, including Micronaut-based builds used during testing, are multi-module and produce noise from many subprojects before any single summary could be emitted.

## Current Tradeoff

The current Gradle design chooses:

- aggressive suppression of task/lifecycle noise
- reliable structured summary generation
- minimal final console output

over:

- preserving the normal Gradle footer and per-task console visibility

That is intentional for the LLM-focused use case.

## Code Map

The main pieces are:

- `llm-build-compactor-gradle-plugin/src/main/java/io/llmcompactor/gradle/LlmCompactorPlugin.java`
- `llm-build-compactor-core/src/main/java/io/llmcompactor/core/SummaryWriter.java`
- `llm-build-compactor-core/src/main/java/io/llmcompactor/core/parser/GradleParser.java`
- `llm-build-compactor-core/src/main/java/io/llmcompactor/core/StackTraceCompressor.java`

## Summary

Gradle output suppression in `llm-build-compactor` is achieved by combining:

- root-scoped plugin coordination
- broad task-level log capture
- task-specific quieting for noisy executors
- test-result parsing from XML reports
- a single end-of-build compact summary

That combination is what makes the Gradle path viable in practice, especially for large multi-module builds where raw console output would otherwise dominate the agent context.

---

## Test Summary Suppression (TestCountLogger)

### How It Works

`TestCountLogger.java` registers an `afterSuite` `TestListener` on the `Test` task during
`configureEach`. When the root suite finishes, it reflects into the Gradle `Test` task to find
the internal `org.gradle.api.internal.tasks.testing.logging.TestCountLogger` instance, then
**replaces all interface-typed fields with no-op `Proxy` instances**.

This is necessary because Gradle 9.x's `TestCountLogger.afterSuite(root)` outputs the summary
two ways:
1. `progressLogger.completed()` — captured by replacing `ProgressLogger` field
2. `logger.error(summary())` — captured by replacing `org.slf4j.Logger` field

Both are interface-typed fields, so the blanket "replace all interfaces except Collection/Map
types" approach handles both without needing field-name matching. Collection and Map types
are excluded because `workerFailures` (a `java.util.List`) is consumed by Gradle's task
reporting framework after `afterSuite` completes, and neutering it would cause
`List.stream()` to return `null`. The no-op proxy also has a `defaultReturnValue` helper
that returns proper zero/false for primitive return types to avoid `NullPointerException`
when Gradle queries the neutered objects.

### Key Files

- `src/main/java/io/llmcompactor/gradle/TestCountLogger.java` — suppression logic
- `src/main/java/io/llmcompactor/gradle/BuildOutputSuppressor.java` — calls `suppressTestCountLogger` in `withType(Test.class).configureEach`
- `src/test/java/io/llmcompactor/gradle/LlmCompactorPluginDefaultsTest.java` — `testCountLoggerLineNotInOutput` test
- `src/test/resources/test-project/` — GradleRunner test project (SampleTest with 2 tests, 1 failure)
- `src/test/java/io/llmcompactor/gradle/CrossVersionTest.java` — cross-version validation (8.14.4, 9.3.0, 9.5.1)
- `src/test/java/io/llmcompactor/gradle/TestCountLoggerTest.java` — unit tests for the logger class

### Diagnostics via JUL FINE Logging

`TestCountLogger` has a `java.util.logging.Logger` at `FINE` level that logs if:

- `findTestCountLogger` encounters a non-`List` collection type
- `findTestCountLogger` throws an unexpected exception
- `neuter` throws an unexpected exception

These are silent by default (JUL's default threshold is INFO). To enable:

```bash
./gradlew :llm-build-compactor-gradle-plugin:test \
  -Djava.util.logging.config.file=/path/to/logging.properties
```

With `logging.properties`:

```properties
io.llmcompactor.gradle.TestCountLogger.level = FINE
java.util.logging.ConsoleHandler.level = FINE
```

**Note:** Inside the GradleRunner fork, JUL is bridged through `jul-to-slf4j` → Gradle's
`OutputEventListener` → captured by our own suppression. So the FINE output is caught by
the suppression machinery and won't leak into build output. To see it, check `System.err`
directly, or observe the GradleRunner's captured output at DEBUG level.

### History & Lessons Learned

#### `whenReady`/`doFirst` approach (reverted)
Originally tried `doFirst` and `taskGraph.whenReady`. These fire too early — Gradle's
`TestCountLogger` is created inside `AbstractTestTask.executeTests()`, well after both hooks.

#### `afterSuite` listener approach (current)
Registering a `TestListener` and acting in `afterSuite(root)` is the only timing window
where `TestCountLogger` exists but hasn't yet output its summary.

#### Filter fix (`startsWith("org.gradle.")`)
`findTestCountLogger` was matching our own anonymous `TestCountLogger$1` listener because it
also contains "TestCountLogger" in its class name. Added `name.startsWith("org.gradle.")` as
a guard.

#### `setAccessible(true)` for `completed()`
Gradle's `ProgressLoggerImpl.completed()` is package-private. The reflection call needs
`completedMethod.setAccessible(true)`.

#### SLF4J logger was the missing piece
We were only replacing the `ProgressLogger` field, but the summary is also output via
`logger.error(summary())` in `TestCountLogger.afterSuite(root)`. Changed from
"replace only fields with `completed()` method" to "replace ALL interface fields" to catch
both `progressLogger` and `logger`.

#### `java.util.List` cast assumption
`findTestCountLogger` casts the listener collection to `List<?>`. If Gradle changes the
collection type, the `instanceof List` check silently fails, traversal finds nothing, and
the summary line reappears. The `else` branch logs at FINE level to make this diagnosable.

#### Cross-version compatibility
Tested across Gradle 8.14.4, 9.3.0, and 9.5.1 — summary suppression works on all three.
Gradle 8.x's `TestCountLogger` only uses `progressLogger` for the summary; Gradle 9.x also
uses `logger.error(summary())`. The "replace all interfaces except collections" approach
handles both without field-name matching.

#### Proxy return values for primitives
The no-op proxy `(proxy, method, args) -> null` returned `null` for all methods, but some
Gradle code paths call methods on the neutered objects after `afterSuite` (e.g., accessing
the `ProgressLoggerFactory` to check whether logging is enabled). When that method returns
`boolean`, the auto-unboxing of `null` throws `NullPointerException`. Fixed via a
`defaultReturnValue(Class<?>)` helper that returns proper zero/false for primitive types.

#### Collection/Map interface skip
The `workerFailures` field on `TestCountLogger` is a `java.util.List`. Neutering it caused
`List.stream()` to return `null`, which was consumed by Gradle's test reporting after
`afterSuite`. Collection and Map interfaces are now skipped (not neutered).

### Known Issues

- **`testsRun: 0` in JSON output**: The GradleRunner's JSON shows `testsRun: 0` even though
  tests actually run. This is a `GradleParser` timestamp-filtering issue, separate from the
  summary suppression.
- **`System.setErr(nullPrint)`**: Currently enabled in `BuildOutputSuppressor`. If debugging
  the forked GradleRunner process, comment it out to see stderr from the inner build.
