# Release Guide

## How Versioning Works

- CI manages versions automatically after merge to main
- Maven: `mathieudutour/github-tag-action` bumps the version tag, passed via `-Drevision=`
- Gradle: version is passed via `-PpluginVersion=` from the Maven publish job's output
- `gradle.properties` holds the local development version (`pluginVersion=X.Y.Z`)
- **Commit prefix determines version bump** (conventional commits):
  - `fix:` → patch (0.1.1 → 0.1.2)
  - `feat:` → minor (0.1.1 → 0.2.0)
  - `BREAKING CHANGE` in body → major (0.1.1 → 1.0.0)
  - Ensure the commit prefix matches the intended version bump (use `bump-version.sh` script if needed)

## Pre-Release Checklist (on the release branch)

### 1. Update versions in docs and build files

Versions in docs and build files are set manually to match the upcoming release:

- `README.md` — all version references in Quick Start and Workflow examples
- `gradle.properties` — `pluginVersion` value
- `pom.xml` — `<version>` default value

### 2. Verify builds

```bash
# Maven (from project root)
mvn clean install -Drevision=X.Y.Z

# Gradle plugin
cd gradle-plugin && ../gradlew build --no-daemon
```

### 3. Repository ordering in `.kts` files

All `repositories {}` blocks should prefer remote repos first, with `mavenLocal()` last:

```kotlin
// pluginManagement
gradlePluginPortal()
mavenCentral()
mavenLocal()

// dependencies
mavenCentral()
mavenLocal()
```

### 4. Review staged and unstaged changes

Before committing, review all diffs carefully:

- Ensure no stale/broken linter changes
- Ensure `.gitignore` has no duplicates and ends with a newline
- Ensure Groovy DSL examples use `.set()` syntax (not `=` assignment)

### 5. Merge to main

After merge, CI will:

1. Build and test across Java versions
2. Tag a new version and deploy to Maven Central
3. Publish the Gradle plugin to the Gradle Plugin Portal using the same version

## Post-Release

- Verify the artifact appears on Maven Central
- Verify the plugin appears on the Gradle Plugin Portal
- Test with a clean project (no `mavenLocal()` artifacts)
