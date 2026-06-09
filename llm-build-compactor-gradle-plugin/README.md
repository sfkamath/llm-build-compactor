# llm-build-compactor-gradle-plugin

Gradle plugin that suppresses noisy build output and emits a compact summary for LLM-assisted development.

See [docs/gradle-design.md](../docs/gradle-design.md) for the architecture and suppression strategy.

---

## Module Structure

This module is a Gradle project wrapped by Maven. The Maven `pom.xml` drives CI publish and dependency resolution. All compilation, testing, and packaging is done by Gradle via `exec-maven-plugin`.

```
llm-build-compactor-gradle-plugin/
├── build.gradle                     # Gradle build: deps, publication, test config
├── settings.gradle                  # Includes :llm-build-compactor-core as a composite
├── pom.xml                          # Maven wrapper: installs core to ~/.m2, invokes Gradle
├── src/main/java/                   # Plugin source
├── src/main/resources/
└── src/test/
    ├── java/                        # Unit tests (run by Gradle)
    └── resources/
        └── test-project/            # Minimal Gradle project used by GradleTestKit tests
```

---

## Running Tests

Tests are compiled and run by Gradle, not Maven Surefire.

### All tests

```bash
cd llm-build-compactor-gradle-plugin
../gradlew test
```

### Single test (fast dev loop)

```bash
../gradlew :test --tests "io.llmcompactor.gradle.LlmCompactorPluginDefaultsTest.testCountLoggerLineNotInOutput"
```

This is significantly faster than the Maven integration tests (~3s vs ~15s) and should be the primary iteration loop when working on the init script or plugin behaviour.

To disable the compactor's own output suppression during test runs (to see raw Gradle output):

```bash
../gradlew :test --tests "..." -Dllmce
```

---

## Running Integration Tests in Isolation

Integration tests live in `integration-tests/` at the repo root and are activated via `-Pintegration-tests`.

### Full suite

```bash
# From repo root — build first so JARs exist
./mvnw clean install -DskipTests
./mvnw verify -Pintegration-tests
```

### Single test class

```bash
./mvnw verify -Pintegration-tests \
  -Dtest="io.llmcompactor.it.GradleBuildOutputTests" \
  -Dsurefire.failIfNoSpecifiedTests=false
```

### Single test method

```bash
./mvnw verify -Pintegration-tests \
  -Dtest="io.llmcompactor.it.GradleBuildOutputTests#testNoLintNoise" \
  -Dsurefire.failIfNoSpecifiedTests=false
```

`-Dsurefire.failIfNoSpecifiedTests=false` is required because the Maven reactor includes modules with no matching tests and would otherwise abort.

---

## Test Infrastructure

### Unit tests: `ProjectBuilder`

`LlmCompactorPluginDefaultsTest` uses `org.gradle.testfixtures.ProjectBuilder` to construct an in-memory Gradle project without actually executing tasks. Use this for:

- Plugin extension defaults
- Task configuration (compiler args, logging settings)
- Anything that can be verified at configuration time

### Functional tests: `GradleTestKit`

`LlmCompactorPluginDefaultsTest` also contains `GradleRunner`-based tests that run an actual Gradle build against the minimal project in `src/test/resources/test-project/`. Use this for:

- Output suppression behaviour (e.g. `TestCountLogger` suppression)
- End-to-end plugin behaviour that requires task execution

The init script (`llm-compactor-init.gradle`) is loaded from the classpath (`src/main/resources/`) and pre-installed into the isolated `testKitDir` before each test run. This means **no daemon restart is needed** when iterating on the init script during unit test runs — the resource is read fresh from disk each time.

### Adding a GradleTestKit test

```java
@TempDir Path testKitDir;

@Test
void myTest() throws Exception {
    URL resource = getClass().getClassLoader().getResource("test-project");
    Path projectDir = Paths.get(resource.toURI());

    BuildResult result = GradleRunner.create()
        .withTestKitDir(testKitDir.toFile())
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()          // injects plugin classes from build output
        .withArguments("test", "--no-daemon")
        .buildAndFail();                // or .build() if no test failures expected

    // assert on result.getOutput()
}
```

`withPluginClasspath()` injects `build/classes/java/main` and `build/resources/main` into the test build's classpath, so the plugin is always the current build output.

---

---

## Troubleshooting

### "Could not find io.github.sfkamath:llm-build-compactor-core"

Run from the project root, not from inside this directory. The Maven wrapper installs core to `~/.m2` before invoking Gradle. If it still fails:

```bash
./mvnw -pl llm-build-compactor-core,llm-build-compactor-gradle-plugin -am clean install
```

### "Unsupported class file major version"

Incompatible Java version. Use the correct wrapper:

```bash
../gradlew-java8 clean build     # Java 8
../gradlew-java11 clean build    # Java 11
../gradlew-smart clean build     # auto-detect
```

### Tests pass in unit tests but fail in integration tests

The unit tests use a fresh isolated `testKitDir` per test and pre-install the init script from the classpath. The integration tests use a persistent daemon that may have loaded an older init script. Follow the init script update steps above.
