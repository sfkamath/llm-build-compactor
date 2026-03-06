# Maven Extension Models & Silence Limitations

This document outlines why we moved away from the standard `<extensions>true</extensions>` plugin model in favor of a **Core Extension** managed via `.mvn/extensions.xml`.

## The Goal: Absolute Silence

For an AI agent to consume build diagnostics efficiently, we required "Zero-Banner" silence. This means:
1. No Maven version header (`Apache Maven 3.9.x...`).
2. No Reactor Build Order tables.
3. No SLF4J initialization logs.
4. No `[INFO]` banners for every plugin execution.

## The Problem with `<extensions>true</extensions>`

Initially, we attempted to package the extension inside the Maven Plugin and enable it via the standard plugin declaration:

```xml
<plugin>
    <groupId>io.llmcompactor</groupId>
    <artifactId>llm-compactor-maven-plugin</artifactId>
    <version>0.1.0</version>
    <extensions>true</extensions>
</plugin>
```

### 1. The "Too Late" Problem (Lifecycle)
Maven classifies extensions enabled via `pom.xml` as **Build Extensions**. 

- **Core Extensions** (`.mvn/extensions.xml`) are loaded during the very first stages of Maven's boot sequence.
- **Build Extensions** are only loaded *after* Maven has already started reading the POM and scanning for projects.

By the time a Build Extension is active, Maven has already printed its version banner, the Java version, and the Reactor Build Order to `System.out`. These cannot be "un-printed" or suppressed retroactively.

### 2. SLF4J Re-initialization
To silence `[INFO]` logs, we must re-configure the SLF4J `SimpleLogger`. 
- Maven's logging system is initialized very early.
- Attempting to reset the logger from a Build Extension often fails because the static logger instances are already "baked" into the Maven core classes. 
- Only a Core Extension can intervene early enough to set the `org.slf4j.simpleLogger.defaultLogLevel=off` system property before the logging system is locked.

### 3. ClassLoader Isolation
Build Extensions are loaded into a classloader that is a child of the project classloader. Core Extensions are loaded into the **Maven Core Classloader**. 
- To effectively intercept all events across all modules and suppress output globally, being in the Core Classloader is significantly more robust.

---

## The Solution: The `install` Mojo

Since manually creating `.mvn/extensions.xml` is tedious and error-prone, we implemented the `install` goal:

```bash
mvn io.llmcompactor:llm-compactor-maven-plugin:install
```

### How it works:
1. It detects the project root.
2. It creates the `.mvn/` directory if missing.
3. It writes a proper `extensions.xml` pointing to our `maven-extension` artifact.
4. On the **next** build run, Maven boots our code as a **Core Extension**, achieving 100% silence from the very first byte of output.

## Summary Table

| Feature | Build Extension (`extensions=true`) | Core Extension (`.mvn/extensions.xml`) |
|---------|-----------------------------------|--------------------------------------|
| **Silence Level** | Partial (Logs only) | **Absolute (Zero Banners)** |
| **Boot Timing** | Post-POM Read | **Pre-Initialization** |
| **ClassLoader** | Plugin/Project | **Maven Core** |
| **Setup** | Easy (POM edit) | Manual (or via `install` mojo) |
| **Reliability** | Medium | **High** |
