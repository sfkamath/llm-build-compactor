# Release Guide

## How Versioning Works

- CI manages versions automatically after merge to main
- Maven: `mathieudutour/github-tag-action` bumps the version tag, passed via `-Drevision=`
- Gradle: version is passed via `-PpluginVersion=` from the Maven publish job's output
- `gradle.properties` holds the local development version (`pluginVersion=X.Y.Z`)
- **Commit prefix determines version bump** (conventional commits):
  - `fix:` → patch (0.0.4 → 0.0.5)
  - `feat:` → minor (0.0.4 → 0.1.0)
  - `BREAKING CHANGE` in body → major (0.1.0 → 1.0.0)
  - **No space before the colon** — `feat:` works, `feat :` is not recognised and falls back to patch
  - Ensure the commit prefix matches the intended version bump

## Pre-Release Checklist (on the release branch)

### 1. Update versions in docs and build files

Versions in docs and build files are set manually to match the upcoming release:

- `README.md` — all version references in Quick Start and Workflow examples
- `docs/maven-extension-model.md` — version references
- `docs/development-guide.md` — version examples
- `gradle.properties` — `pluginVersion` value
- `pom.xml` — `<version>` default value

> **Note:** Version references in docs can drift out of sync between releases. Always check the Maven Central and Gradle Plugin Portal badges in the README for the true latest published version.

### 2. Verify builds

```bash
# Maven (from project root)
mvn clean install -Drevision=X.Y.Z

# Gradle plugin
cd llm-build-compactor-gradle-plugin && ../gradlew build --no-daemon
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

---

## Checklist: Flip Publishing from GitHub Packages → Maven Central + Gradle Portal

> Currently publishing to GitHub Packages for validation. Complete every item below before flipping.

### Prerequisites

- [ ] `feature/validate-publish` merged to main and CI green
- [ ] GitHub Packages publish fired and a new version landed (check Actions tab)
- [ ] Install mojo end-to-end test passed:
  ```bash
  cd test-project-maven
  mvn llm-compactor:install        # extensions.xml must contain the plugin version, not 1.0.0-SNAPSHOT
  cat .mvn/extensions.xml          # verify version matches published version
  mvn test                         # must run without resolution errors
  ```
- [ ] Gradle plugin end-to-end test passed:
  ```bash
  cd test-project-gradle
  GITHUB_ACTOR=$(gh api user --jq .login) GITHUB_TOKEN=$(gh auth token) \
    ../gradlew-smart test -PusePublished -PpluginVersion=<published_version>
  ```
- [ ] Smoke tests CI job green: `./mvnw install -Psmoke-tests`

### Secrets (verify all are set in GitHub repo settings)

- [ ] `GPG_PRIVATE_KEY` — re-export if in doubt: `gpg --armor --export-secret-keys <KEY_ID> | gh secret set GPG_PRIVATE_KEY`
- [ ] `GPG_PASSPHRASE` — must be empty string `""` if key has no passphrase (not absent — set to empty)
- [ ] `CENTRAL_USERNAME` — Sonatype Central portal token username
- [ ] `CENTRAL_PASSWORD` — Sonatype Central portal token password
- [ ] `GRADLE_PUBLISH_KEY` — from plugins.gradle.org
- [ ] `GRADLE_PUBLISH_SECRET` — from plugins.gradle.org

### The flip (one PR, three one-line changes)

In `publish-maven-central.yml`:
```yaml
if: github.event.workflow_run.conclusion == 'success'   # was: if: false
```

In `publish-gradle-portal.yml`:
```yaml
if: github.event.workflow_run.conclusion == 'success'   # was: if: false
```

In `publish-github-packages.yml`:
```yaml
if: false   # was: if: github.event.workflow_run.conclusion == 'success'
```

### After merge

- [ ] Monitor Actions tab — Maven Central publish fires first, then Gradle Portal chains off it
- [ ] Verify artifact on Maven Central (may take up to 30 min to sync)
- [ ] Verify plugin on Gradle Plugin Portal
- [ ] Test with a clean project (no `mavenLocal()`, no GitHub Packages credentials)
- [ ] Update README badges if version jumped significantly
- [ ] Apply foojay article changes (stashed on `foojay` branch)
