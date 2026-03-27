# Integration Tests

This module contains end-to-end integration tests for the Maven and Gradle plugins. Each test spawns a real build subprocess against a self-contained test project and asserts on what the plugin emits.

---

## How They Work

### The test projects

Two test projects live in `src/test/resources/test-projects/`:

| Project | Build tool | Tests |
|---|---|---|
| `gradle-test-project` | Gradle (Groovy DSL) | `GradleOptionTests` |
| `maven-test-project` | Maven | `MavenOptionTests` |

Both projects are **deliberately broken**: they contain `OrderServiceTest`, `PaymentServiceTest`, `StackTraceTest`, and integration tests that intentionally fail with assertion errors, exceptions, and logging output. This is the contract the integration tests rely on — the plugin must see failures in order to produce `fixTargets`, `testLogs`, `errors`, etc. **Do not fix the intentional failures in these test projects.**

Each test project also uses Lombok (annotation processor) and SLF4J/Logback to exercise the plugin's log-stripping and stack-trace compression features.

### The build helpers

`GradleBuild` and `MavenBuild` are fluent builders that spawn a real `./gradlew` or `./mvnw` subprocess:

```java
BuildResult result = GradleBuild.inProject("gradle-test-project")
    .withTask("test")
    .withProperty("llmCompactor.mode", "agent")
    .execute();
```

`execute()`:
1. Deletes the `build/` or `surefire-reports/` directory so each test starts clean
2. Assembles a command (`./gradlew --daemon --no-build-cache --no-configuration-cache ...` or `./mvnw -B -q ...`)
3. Spawns the process, captures stdout+stderr merged into a single string
4. Waits up to 5 minutes (configurable)
5. Calls `BuildResult.extractJsonFromOutput()` to pull the plugin's JSON summary out of the output

### JSON extraction

`BuildResult.extractJsonFromOutput(output)` finds the **first `{`** and **last `}`** in the captured output and returns that substring as `summaryJson`. This is why output suppression matters: if the build emits any `{` or `}` characters before the plugin's summary JSON, the extraction produces garbage. The plugin redirects `System.out`/`System.err` to a null stream during the build and restores them before emitting the summary, ensuring the JSON is the only `{}` content in the output.

### The Gradle test home (`GRADLE_TEST_HOME`)

`GradleBuild` maintains a shared, isolated Gradle user home at `/tmp/llm-compactor-gradle-test-home`. This is passed as `GRADLE_USER_HOME` to every Gradle subprocess. It:

- **Isolates tests** from the developer's real `~/.gradle` cache
- **Caches downloaded dependencies** across tests in the same suite run (so only the first test pays the download cost)
- **Seeds Gradle wrapper distributions** from the real `~/.gradle/wrapper/dists` at static initialisation time, avoiding Gradle distribution downloads
- **Receives the auto-installed init script** — when the plugin runs its first build against `GRADLE_TEST_HOME`, it installs `llm-compactor-silence.gradle` into `GRADLE_TEST_HOME/init.d/`, not `~/.gradle/init.d/`. The developer's real home is never touched.

The init script lifecycle tests (`GradleOptionTests.InitScriptTests`) verify that:
- Applying the plugin auto-installs the init script into `GRADLE_TEST_HOME/init.d/`
- `installLlmCompactor` and `uninstallLlmCompactor` correctly write/remove it
- `uninstallLlmCompactor` is a no-op when the script is already absent

---

## Running the Tests

### You cannot run a single test in isolation

Each `@Test` method in `GradleOptionTests` and `MavenOptionTests` is inside a `@Nested` inner class. More importantly, the test projects reference the plugin under test via `@project.version@` tokens (Maven resource filtering) and require the plugin JARs to already be installed in the local Maven repository. Both of these are set up by the **`process-test-resources` phase of the `integration-tests` module**, which:

1. Filters `build.gradle` and `pom.xml` files, replacing `@project.version@` with the current version
2. Runs `maven-install-plugin:install-file` four times to install the following into local Maven repo:
   - `llm-build-compactor-core-${version}.jar`
   - `llm-build-compactor-gradle-plugin-${version}.jar`
   - `llm-build-compactor-maven-plugin-${version}.jar`
   - `llm-build-compactor-extension-${version}.jar`

Those JARs must already exist in the sibling modules' `target/` directories, which means **the full reactor must be built first**. Running `integration-tests` alone without first building the rest fails because the JARs do not exist.

### Correct way to run

```bash
# From the repo root — builds everything and runs all tests including integration tests
./mvnw clean install
```

If you only want to re-run integration tests after the full build has already run:

```bash
./mvnw -pl integration-tests test
```

This works because the JARs are already in local Maven repo from the previous full build. The `process-test-resources` phase re-installs them (idempotent) and re-filters the test resources.

### What the test run looks like

Each `@Test` method spawns a full Gradle or Maven build. Expect each test to take **30–90 seconds**. With ~40 tests split across two test classes and running sequentially, a full integration-test run takes **20–40 minutes** on a warm daemon.

Gradle tests reuse a daemon scoped to `GRADLE_TEST_HOME`, so the second test in the suite is faster than the first (daemon warm-up already done).

---

## Debugging a Failing Test

You cannot run a single test method quickly. The practical approach is:

1. Add a descriptive `.as(...)` to the failing assertion so the error message includes the full build output:

```java
assertThat(tree.has("fixTargets"))
    .as("fixTargets absent. json=%s\n\noutput=\n%s", result.summaryJson(), result.output())
    .isTrue();
```

2. Run the full suite: `./mvnw clean install`
3. The Surefire report for the failing test will contain the full Gradle/Maven build output inline in the failure message.

The full output tells you whether:
- **`compileJava` failed** (Lombok or other compile error) — no test XML results, empty `errors` array, `fixTargets` absent
- **Tests ran but all passed** — no failures, empty `errors`, `fixTargets` absent
- **JSON extraction grabbed the wrong content** — `summaryJson` is non-null but malformed or missing expected fields

### Common failure: Lombok + JDK incompatibility

Lombok versions below 1.18.32 fail on JDK 17+ with:
```
NoSuchFieldException: com.sun.tools.javac.code.TypeTag :: UNKNOWN
```
This causes `compileJava` to fail, producing no test XML results. The plugin then emits a summary with no `errors` and no `fixTargets`, breaking every test that checks for those fields.

**Fix:** Keep Lombok at 1.18.32 or newer in both test projects (`build.gradle` and `pom.xml`).

Note: Gradle's configuration cache (enabled by default in Gradle 9.4+) can mask this failure by replaying a cached task graph from a previous successful run when the init script was installed but tests had never compiled cleanly. The `--no-configuration-cache` flag in `GradleBuild.execute()` prevents this masking, ensuring `compileJava` actually runs on each test invocation.

### Common failure: init script not present

Some tests (those checking `fixTargets`, `testLogs`, `testDurationPercentiles`) depend on the plugin suppressing all Gradle output so that `BuildResult.extractJsonFromOutput()` finds only the plugin's JSON. If the init script is absent, Gradle lifecycle output may interleave with the JSON, corrupting extraction.

The auto-install in `apply()` handles this for the first test in the suite — subsequent tests benefit from the daemon already having loaded the init script. If these tests fail unexpectedly, check that `GRADLE_TEST_HOME/init.d/llm-compactor-silence.gradle` is present after the first test runs.

---

## Adding a New Test

1. Add a `@Test` method in the appropriate `@Nested` class in `GradleOptionTests` or `MavenOptionTests`
2. Use `GradleBuild.inProject("gradle-test-project")` or `MavenBuild.inProject("maven-test-project")`
3. If your test needs a field that only appears when tests fail (e.g. `fixTargets`, `testLogs`), it depends on the test project having intentional failures — that contract is already met
4. If your test needs a new plugin option, add the corresponding property with `.withProperty("llmCompactor.yourOption", "value")`

Do not create new test projects unless absolutely necessary — the existing ones cover the plugin's full surface area and each new project adds significant CI time.

---

## Module Structure

```
integration-tests/
  pom.xml                          # Maven module; coordinates install-file executions
  src/test/java/io/llmcompactor/it/
    GradleBuild.java               # Fluent builder: spawns ./gradlew subprocesses
    MavenBuild.java                # Fluent builder: spawns ./mvnw subprocesses
    BuildResult.java               # Holds captured output, summaryJson, exitCode
    GradleOptionTests.java         # All Gradle plugin option tests (@Nested classes)
    MavenOptionTests.java          # All Maven plugin option tests (@Nested classes)
  src/test/resources/test-projects/
    gradle-test-project/           # Self-contained Gradle project with intentional failures
      build.gradle                 # Uses @project.version@ (filtered at build time)
      src/main/java/...            # Lombok-annotated production code
      src/test/java/...            # Intentionally failing JUnit 5 tests
      src/it/java/...              # Intentionally failing integration tests
    maven-test-project/            # Self-contained Maven project with intentional failures
      pom.xml                      # Uses @project.version@ (filtered at build time)
      .mvn/extensions.xml          # Uses @project.version@ (filtered at build time)
      src/...                      # Same structure as gradle-test-project
```
