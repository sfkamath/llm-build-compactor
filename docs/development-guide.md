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

## Building the Project

### Quick Build (Your Current Java Version)

```bash
# Maven (all modules)
./mvnw clean install

# Gradle plugin only (Java 17+)
cd gradle-plugin && ../gradlew clean build
```

### Build with Quality Checks

Runs SpotBugs and other quality gates (Java 21+ recommended):

```bash
./mvnw clean verify -Pquality
```

### Build for Specific Java Version

```bash
# Using jenv or similar tool
export JAVA_HOME=~/.jenv/versions/1.8.0.351
./mvnw clean install

# Or use the test script (see below)
./scripts/test-all-java-versions.sh
```

---

## Testing

### Test Projects

Two test projects verify plugin behavior:

| Project | Build Tool | Purpose |
|---------|------------|---------|
| `test-project/` | Maven | Tests Surefire/Failsafe parsing, stack traces, Lombok |
| `test-project-gradle/` | Gradle | Tests Gradle plugin integration |

**Important:** These projects contain **intentional test failures** to verify the compactor correctly parses errors:

- `OrderServiceTest.java` - AssertionError (wrong expected value)
- `StackTraceTest.java` - NullPointerException, IllegalStateException, etc.
- `PaymentIT.java` - AssertionError (wrong refund amount)
- `OrderProcessorIT.java` - AssertionError (wrong total)

### Running Test Projects

```bash
# Maven test project
cd test-project
mvn clean verify

# View output
cat target/llm-summary.json

# Gradle test project (Java 17+)
cd test-project-gradle
../gradlew-smart clean test
```

### Test Scripts

| Script | Purpose | Duration |
|--------|---------|----------|
| `scripts/test-quick.sh` | Runs all tests on Java 8, 11, 17, 21, 25 | ~10 min |
| `scripts/test-comprehensive.sh` | Full matrix + test projects | ~20 min |
| `scripts/test-all-java-versions.sh` | Legacy comprehensive test | ~20 min |

**Recommended:** Use `test-quick.sh` for local development.

```bash
./scripts/test-quick.sh
```

---

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`) runs on every push/PR:

- **Build matrix:** Java 8, 11, 17, 21, 25
- **Quality checks:** SpotBugs on Java 21
- **Publish:** Automatic to Maven Central + Gradle Plugin Portal (on main branch merge)

### Version Bumping

Versions follow [Conventional Commits](https://www.conventionalcommits.org/):

| Commit Prefix | Version Bump | Example |
|---------------|--------------|---------|
| `fix:` | Patch | 0.1.2 → 0.1.3 |
| `feat:` | Minor | 0.1.1 → 0.2.0 |
| `BREAKING CHANGE` in body | Major | 0.1.1 → 1.0.0 |

CI automatically bumps versions on merge to main. See [`RELEASE-GUIDE.md`](RELEASE-GUIDE.md) for details.

---

## Module Structure

```
llm-build-compactor/
├── core/                          # Core parsing/compaction logic (Java 8+)
├── maven-extension/               # Maven extension for build silence (Java 8+)
├── llm-compactor-maven-plugin/    # Maven Mojo (Java 8+)
├── gradle-plugin/                 # Gradle plugin (Java 17+ for Gradle API)
├── test-project/                  # Maven test project with intentional failures
├── test-project-gradle/           # Gradle test project
└── scripts/                       # Test automation scripts
```

### Key Design Decisions

1. **Core is Java 8:** Maximum compatibility for parsing logic
2. **Gradle plugin is Java 17+:** Modern Gradle API requires it
3. **Maven components are Java 8:** Maven 3.8+ supports Java 8
4. **Test projects have failing tests:** Intentional - verifies error parsing

---

## Common Development Tasks

### Add a New Configuration Option

1. Add constant to `CompactorDefaults.java`
2. Add field to Maven Mojo (`@Parameter`)
3. Add property to Gradle Extension
4. Add to Maven Extension (`boolProp` calls)
5. Update README table
6. Update this guide if Java version requirements change

### Debug Plugin Behavior

```bash
# Enable debug logging
mvn clean verify -X | tee build.log

# Or for Gradle
gradlew clean test --debug
```

### Test Java 8 Compatibility

```bash
export JAVA_HOME=~/.jenv/versions/1.8.0.351
./mvnw clean install
```

Ensure no Java 11+ APIs are used (e.g., `List.of()`, `var`, text blocks).

### Run SpotBugs Locally

```bash
# Java 21+
./mvnw spotbugs:check -Pquality
```

---

## Troubleshooting

### "Unsupported class file major version"

You're running Gradle with an incompatible Java version. Use the correct wrapper:

```bash
# Java 8
./gradlew-java8 clean build

# Java 11
./gradlew-java11 clean build

# Java 17+
./gradlew clean build

# Or let it auto-detect
./gradlew-smart clean build
```

### Test Project Shows 0 Failures

The compactor might be disabled. Check:

```bash
mvn clean verify -DllmCompactor.enabled=false  # Should show raw Maven output
mvn clean verify                               # Should show compactor summary
```

### Gradle Plugin Not Appearing

Ensure you're running from the `gradle-plugin` directory or have applied it correctly:

```kotlin
// build.gradle.kts
plugins {
    id("io.llmcompactor.gradle") version "0.1.3"
}
```

---

## Contributing

1. Create a feature branch
2. Make changes (follow existing code style)
3. Run `./scripts/test-quick.sh` to verify all Java versions
4. Submit PR

CI will run full validation on PR creation.
