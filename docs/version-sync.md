Two distinct sync problems exist in this repo:

1. **Plugin version**: must match across 4 files for local dev
2. **Dependency versions**: versions hardcoded in Gradle files that duplicate Maven `<properties>`

---

## Problem 1 — Plugin version (4 files, manually kept in sync)

For local development, `0.4.0` must be consistent across:

| File | Property |
|------|----------|
| `pom.xml` | `<revision>0.4.0</revision>` |
| `gradle.properties` | `pluginVersion=0.4.0` |
| `test-project-maven/pom.xml` | `<llmCompactor.pluginVersion>0.4.0</llmCompactor.pluginVersion>` |
| `test-project-maven/.mvn/extensions.xml` | `<version>0.4.0</version>` |

`test-project-gradle/settings.gradle` is already wired correctly — it reads `pluginVersion`
from `gradle.properties` via `providers.gradleProperty`, so no manual sync needed there.

CI bypasses all of this: Maven receives `-Drevision=<tag>` and Gradle receives
`-PpluginVersion=<tag>` from the tag action output. The local values are dev defaults only.

### Proposed fix: `build.sh` to bridge `gradle.properties` → Maven `${revision}`

```bash
#!/usr/bin/env bash
PLUGIN_VERSION=$(grep "^pluginVersion=" gradle.properties | cut -d'=' -f2)
exec ./mvnw -Drevision="$PLUGIN_VERSION" "$@"
```

`gradle.properties` becomes the single place to bump the version locally.
`test-project-maven/pom.xml` and `.mvn/extensions.xml` still need manual updates —
they are standalone projects outside the root Maven multi-module build and cannot
inherit from root.

---

## Problem 2 — Dependency versions duplicated across Maven and Gradle

### What actually works

`gradle.properties` is Gradle's native config file. It works as a shared source **only for
Gradle builds**. Maven cannot use it for version strings.

> **Hard constraint**: Maven validates all `<version>` values (dependencies AND plugins) at
> POM model-build time — before any lifecycle phase runs. Properties loaded by
> `properties-maven-plugin` at `initialize` phase arrive too late. Any `${property}` in a
> `<version>` tag must be defined in the POM's own `<properties>` section (or inherited from
> a parent POM's `<properties>`).

### How the reactor handles it

Root `pom.xml` declares shared versions in `<properties>`. All reactor child modules inherit
them at model-build time — no plugin loading needed.

```xml
<!-- pom.xml <properties> -->
<junitVersion>5.14.4</junitVersion>
<assertjVersion>3.27.7</assertjVersion>
<jacksonVersion>2.21.4</jacksonVersion>
```

### How Gradle builds read versions

Gradle reads `gradle.properties` natively. The same keys declared there are used directly
in `build.gradle` files:

```groovy
def rootProps = new Properties()
rootProps.load(new FileInputStream(file('../gradle.properties')))

dependencies {
    implementation "com.fasterxml.jackson.core:jackson-databind:${rootProps['jacksonVersion']}"
    testImplementation "org.junit.jupiter:junit-jupiter:${rootProps['junitVersion']}"
}
```

### Standalone test projects

`test-project-maven` and `test-project-gradle` have no parent pom. Each must declare its
own version properties statically. They contain a comment noting which values must be kept
in sync with `gradle.properties` when bumping.

### The duplication is intentional

- `gradle.properties` is the source of truth **for Gradle**
- Root `pom.xml` `<properties>` is the source of truth **for the Maven reactor**
- `test-project-maven/pom.xml` `<properties>` is the source of truth **for that standalone project**

These cannot be unified via `properties-maven-plugin` due to Maven's model-build timing.
Bumping a shared dependency requires editing both `gradle.properties` and root `pom.xml`.

See [Module Structure](development-guide.md#module-structure) in the development guide.
