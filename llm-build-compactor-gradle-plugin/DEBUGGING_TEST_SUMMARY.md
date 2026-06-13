# Debugging & Verifying Test Summary Suppression

## Goal
The goal is to suppress the internal Gradle test summary line (e.g., `2 tests completed, 1 failed`) which is normally output by Gradle's internal `TestCountLogger`. This is necessary to provide a clean, LLM-optimized build output.

## Strategy
We use reflection to locate the internal `TestCountLogger` listener attached to the Gradle `Test` task. Once found, we:
1.  Identify its `ProgressLogger`, `org.slf4j.Logger`, and any other interface-typed fields.
2.  Call `completed()` on the `ProgressLogger` to close its lifecycle gracefully.
3.  Replace all interface-typed fields (except Collection/Map types) with a **no-op Proxy** that returns proper defaults for primitive types.

This logic is executed via an `afterSuite` `TestListener` registered on the `Test` task during `configureEach` — the only timing window where `TestCountLogger` exists but hasn't yet output its summary.

For full design details and history, see `docs/gradle-design.md`.

## Verification Workflow

### 1. Build and Install the Plugin
Build the core library and the plugin, installing them to the local Maven repository so the test project can resolve the updated code.
```bash
./mvnw clean install -DskipTests
```

### 2. Run the Embedded Test Case
The plugin's test suite validates suppression across Gradle 8.14.4, 9.3.0, and 9.5.1 using `GradleRunner`.
```bash
# Cross-version test (recommended)
./gradlew :llm-build-compactor-gradle-plugin:test \
  --tests "io.llmcompactor.gradle.CrossVersionTest"

# Dedicated suppression test
./gradlew :llm-build-compactor-gradle-plugin:test \
  --tests "io.llmcompactor.gradle.LlmCompactorPluginDefaultsTest.testCountLoggerLineNotInOutput"
```

### 3. Check Test Results
Read the JUnit XML files directly — the Gradle runner's JSON summary (`testsRun: 0`) is unreliable for counting actual JUnit tests.
```bash
cat llm-build-compactor-gradle-plugin/build/test-results/test/TEST-*.xml
```

## Troubleshooting
If the summary line still appears:
- **Test Discovery**: Check if the plugin is actually picking up tests (the output should show `testsRun > 0`). If it shows 0, check `GradleParser` timestamp filtering.
- **Reflection Failure**: The internal Gradle class names or field names may have changed. The `TestCountLogger.java` uses broad matching (`name.contains("TestCountLogger")`) guarded by `name.startsWith("org.gradle.")` — this may need updates for newer Gradle versions.
- **Timing**: If the line appears at the very end, the `neuter()` call might be happening after Gradle has already fired the final event. Check that the `afterSuite` listener fires before the summary is output.
- **Diagnostics**: Enable JUL FINE logging for `io.llmcompactor.gradle.TestCountLogger` to see diagnostic output from the reflection traversal. See `docs/gradle-design.md` for details.
- **`workerFailures`**: If the build shows `Cannot invoke "java.util.stream.Stream.map..."`, the `workerFailures` List is being neutered. Check that Collection/Map types are excluded from the proxy replacement.
