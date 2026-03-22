# Development Guide

This guide covers building, testing, and developing the LLM Build Compactor across all supported Java versions.

## Java Version Support

The compactor supports **Java 8 through 25** (latest LTS + recent releases).

### Gradle Wrapper Strategy

Different Gradle versions are required for different Java versions:

| Java Version | Gradle Version | Wrapper Script |
|--------------|----------------|----------------|
| 8            | 8.0.2          | `./gradlew-java8` |
| 11           | 8.5            | `./gradlew-java11` |
| 17+          | 8.5+           | `./gradlew` (default) |

**Helper Script:** `./gradlew-smart` automatically detects your Java version and selects the correct wrapper.

### Maven

Maven works across all Java versions with a single wrapper: `./mvnw`

---

## Version Management

The project uses a single version defined in `pom.xml`:

```xml
<revision>0.0.1-SNAPSHOT</revision>
```

- All Maven modules inherit this via `${revision}` / `${project.version}`
- The flatten plugin (`resolveCiFriendliesOnly`) resolves `${revision}` into `.flattened-pom.xml` at build time
- The Gradle plugin receives the version from Maven via `-PpluginVersion=${project.version}` — `gradle.properties` does **not** carry a version
- At CI release time, the version is overridden: `mvn deploy -Drevision=<new_tag>`
- The `pom.xml` revision value is a **local dev default only**; CI bumps the actual version at deploy time

### Why Gradle needs install-file

`llm-build-compactor-gradle-plugin` wraps a Gradle build via `exec-maven-plugin`. Gradle runs as an **external subprocess** and cannot see the Maven reactor — it must resolve `core:${version}` from a repository (`mavenCentral()` or `mavenLocal()`). The `llm-build-compactor-gradle-plugin/pom.xml` therefore runs two `install-file` executions at `process-resources` before Gradle builds:

1. The root POM — because `core`'s flattened POM still has `<parent>`, so Gradle fetches it too
2. The `core` JAR + its flattened POM

### Standalone test-project-maven versioning

`test-project-maven/.mvn/extensions.xml` uses a **hardcoded** version (e.g. `0.0.1`) because that file is loaded by Maven before the build lifecycle — property resolution and resource filtering don't apply. Update it manually when bumping the version for local testing. CI uses the published release version.

---

## Building the Project

### Standard Build

```bash
./mvnw clean verify
```

Works on a clean machine with an empty local repo. The `llm-build-compactor-gradle-plugin` module
installs `core` and the root POM to local repo before invoking Gradle, so no
prior `mvn install` is required.

### Build with Quality Checks

Runs SpotBugs, Spotless, Modernizer, and JaCoCo coverage gates (Java 21+ recommended):

```bash
./mvnw clean verify -Pquality
```

### Build for Specific Java Version

```bash
export JAVA_HOME=~/.jenv/versions/1.8.0.351
./mvnw clean verify
```

---

## Testing

### Unit Tests

Run as part of the standard build:

```bash
./mvnw clean verify
```

### Integration Tests

Integration tests live in `integration-tests/` and are **not** part of the default
reactor. They are activated via the `integration-tests` profile:

```bash
# Build all modules first (JARs must exist in target/ before integration tests run)
./mvnw install -DskipTests
./mvnw verify -Pintegration-tests
```

The `integration-tests` module is **self-contained**: its `pom.xml` runs `install-file`
executions at `process-test-resources` to install the root POM, `core`,
`llm-build-compactor-maven-plugin`, and `llm-build-compactor-extension` into `~/.m2` before any test
subprocess launches.

Resource filtering substitutes `@project.version@` into the test projects at
`process-test-resources` time, ensuring tests always run against the locally-built version.
Only `@...@` delimiters are used — **never `${...}`** — to avoid Maven expanding expressions
with the integration-tests module's own properties (see Troubleshooting below).

### Test Projects

| Project | Build Tool | Purpose |
|---------|------------|---------|
| `test-project-maven/` | Maven | Standalone; tests Surefire/Failsafe parsing, stack traces, Lombok |
| `integration-tests/src/test/resources/test-projects/maven-test-project/` | Maven | Filtered copy used by integration tests |
| `integration-tests/src/test/resources/test-projects/gradle-test-project/` | Gradle | Filtered copy used by integration tests |

**Important:** These projects contain **intentional test failures** to verify the compactor
correctly parses errors:

- `OrderServiceTest.java` — AssertionError (wrong expected value)
- `StackTraceTest.java` — NullPointerException, IllegalStateException, etc.
- `PaymentIT.java` — AssertionError (wrong refund amount)
- `OrderProcessorIT.java` — AssertionError (wrong total)

```bash
cd test-project-maven && mvn clean verify
cat target/llm-summary.json
```

---

## Module Structure

```
llm-build-compactor/
├── pom.xml                              # Parent POM; version=${revision}
├── gradle.properties                    # Gradle wrapper config only (no version)
├── core/                                # Core parsing/compaction logic (Java 8+)
├── llm-build-compactor-extension/       # Maven extension for build silence (Java 8+)
├── llm-build-compactor-maven-plugin/    # Maven Mojo (Java 8+)
├── llm-build-compactor-gradle-plugin/   # Gradle plugin (Java 17+); wraps Gradle build via Maven
├── integration-tests/                   # Integration tests (not in default reactor; -Pintegration-tests)
└── test-project-maven/                  # Standalone Maven test project with intentional failures
```

### Key Design Decisions

1. **Core is Java 8:** Maximum compatibility for parsing logic
2. **Gradle plugin is Java 17+:** Modern Gradle API requires it
3. **Maven components are Java 8:** Maven 3.8+ supports Java 8
4. **Test projects have failing tests:** Intentional — verifies error parsing
5. **Gradle gets version from Maven:** `llm-build-compactor-gradle-plugin/pom.xml` passes `-PpluginVersion=${project.version}`; `gradle.properties` no longer carries a version to avoid drift
6. **Integration test resources use `@...@` tokens only:** Prevents Maven resource filtering from expanding `${project.build.directory}` and similar properties with the parent module's values

---

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`) runs on every push/PR:

- **Build matrix:** Java 8, 11, 17, 21, 25
- **Quality checks:** SpotBugs, Spotless, Modernizer, JaCoCo on Java 21
- **Integration tests:** Java 17 job after the build matrix
- **Publish:** Automatic to GitHub Packages on main branch merge

### Version Bumping

Versions follow [Conventional Commits](https://www.conventionalcommits.org/):

| Commit Prefix | Version Bump | Example |
|---------------|--------------|---------|
| `fix:` | Patch | 0.0.1 → 0.0.2 |
| `feat:` | Minor | 0.1.1 → 0.2.0 |
| `BREAKING CHANGE` in body | Major | 0.1.1 → 1.0.0 |

CI bumps the version via `mathieudutour/github-tag-action` and passes it to Maven
as `-Drevision=<new_tag>`. The `pom.xml` revision value is a local dev default only.

---

## Common Development Tasks

### Add a New Configuration Option

1. Add constant to `CompactorDefaults.java`
2. Add field to Maven Mojo (`@Parameter`)
3. Add property to Gradle Extension
4. Add to Maven Extension (`boolProp` calls)
5. Update README table

### Debug Plugin Behavior

```bash
# Maven verbose
./mvnw clean verify -X 2>&1 | tee build.log

# Gradle verbose
cd llm-build-compactor-gradle-plugin && ../gradlew-smart clean build --info -PpluginVersion=0.0.1-SNAPSHOT
```

### Run SpotBugs Locally

```bash
./mvnw spotbugs:check -Pquality
```

---

## Troubleshooting

### gradle-plugin fails with "Could not find io.github.sfkamath:core"

This should not happen with the current setup — `llm-build-compactor-gradle-plugin/pom.xml` installs
`core` and the root POM to `~/.m2` before invoking Gradle. If it does occur:

1. Confirm you are running `./mvnw` from the project root (not inside `llm-build-compactor-gradle-plugin/`)
2. Run with `-pl core,llm-build-compactor-gradle-plugin -am` to isolate the two modules

### "Unsupported class file major version"

You're running Gradle with an incompatible Java version. Use the correct wrapper:

```bash
./gradlew-java8 clean build    # Java 8
./gradlew-java11 clean build   # Java 11
./gradlew-smart clean build    # auto-detect
```

### Test Project Shows 0 Failures

The compactor might be disabled. Check:

```bash
mvn clean verify -DllmCompactor.enabled=false  # raw Maven output
mvn clean verify                               # compactor summary
```

### Integration tests show testsRun=0

This usually means the surefire/failsafe XML reports weren't written to the expected
location. The most common cause: a `${project.build.directory}` expression in a
test-project-maven `pom.xml` was expanded by Maven's resource filter (running in the context
of `integration-tests`) to `integration-tests/target` rather than the subprocess
project's own `target/`. Always use literal relative paths like `target/surefire-reports`
in test-project-maven pom.xml files — never `${project.build.directory}`.

### "Could not find io.github.sfkamath:llm-build-compactor-maven-plugin:@project.version@"

The `build.gradle` token was not substituted. Confirm that `**/build.gradle` is listed
in the filtered includes in `integration-tests/pom.xml`, and re-run
`./mvnw -pl integration-tests process-test-resources` to regenerate filtered resources.
