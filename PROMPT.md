# Build Repair Agent Instructions

This repository produces structured diagnostics for build failures.

Agents should follow this workflow.

## Workflow

1. Run the build

mvn verify
or
gradle build

2. Read the file

target/llm-summary.json
or
build/llm-summary.json

3. Inspect the `fixTargets` section.

Each fix target contains:

- file
- line
- reason
- snippet

4. Only read the relevant file region if needed.

Do NOT ingest the entire build logs.

5. Apply fixes iteratively.

After each fix:

run build again  
re-read summary file

## Fix Strategy

COMPILATION_ERROR  
→ update source file

TEST_FAILURE  
→ inspect failing test or source location

DEPENDENCY_ERROR  
→ modify pom.xml or build.gradle

## Important

Always prioritise:

- compilation errors
- failing tests
- dependency failures

Ignore informational logs.

The summary file is the canonical diagnostic source.

## Recommended Workflow

Use the helper script:

tools/run-agent-build.sh

This will:

1. run the build
2. capture logs
3. produce llm-summary.json
4. show fix targets

Agents should inspect:

fixTargets[]

---

## Automated Repair Loop

Use:

tools/agent-repair-loop.sh

Loop behaviour:

1 run build  
2 inspect llm-summary.json  
3 apply fix  
4 repeat until success

---

## Prioritisation Strategy

Agents should prioritise:

1 compilation errors  
2 failing tests  
3 files recently modified (git diff)

Ignore unrelated modules.

---

## Minimal Context Rule

Only read:

- snippet
- failing file
- related test

Avoid loading entire repositories.

---

## Plugin Requirements

The LLM Build Compactor plugin must:

### Invocation

- **No special command** - Plugin activates automatically during normal Maven builds
- Works with any goal: `mvn verify`, `mvn test -Dtest=MyTest`, `mvn spotless:apply`, etc.
- Optional: Can be enabled/disabled via profile `-Pllm-compactor`

### Output

- **Only JSON to stdout** - No Maven banners, no plugin logs, no build status
- Write `target/llm-summary.json` (Maven) or `build/llm-summary.json` (Gradle)
- Suppress all other output:
  - Maven lifecycle logs (`[INFO]`, `[DEBUG]`, `[WARNING]`)
  - Plugin output (compiler, surefire, failsafe, etc.)
  - Application logs (System.out, System.err, Logback, SLF4J)

### Capture

- Compilation errors from all phases
- Test failures (Surefire unit tests, Failsafe integration tests)
- Stack traces with project frames preserved
- Code snippets showing failing lines

### Design Constraints

- **No build forking** - Plugin runs as part of normal build, doesn't re-run it
- **Zero configuration** - Use `mvn io.llmcompactor:llm-compactor-maven-plugin:install` to setup Core Extension
- **Transparent passthrough** - All Maven goals execute normally

### Architecture: Core Extension + Mojo

**Extension (`BuildOutputSpy`):**
- Implements `EventSpy` and `AbstractMavenLifecycleParticipant`
- Suppression starts at `afterProjectsRead`
- Re-initializes SLF4J to silence Maven logs
- Redirects `System.out` to `OutputStream.nullOutputStream()`
- Collects `MojoFailed` events for compilation errors

**Plugin Mojo (`LlmCompactMojo`):**
- Runs at `VERIFY` phase (as a fallback)
- Collects test reports from `target/surefire-reports`
- Generates `BuildSummary`
- Restores `System.out` to print JSON to stdout

The Core Extension (`maven-extension`) handles complete silence of Maven logging by re-initializing SLF4J and suppressing `System.out`. To use it:

1. Install the extension to your project: `mvn io.llmcompactor:llm-compactor-maven-plugin:0.1.0:install`
2. Run your build: `mvn verify -DllmCompactor.outputAsJson=true`

### Configuration Properties

- `llmCompactor.enabled` (default: true)
- `llmCompactor.outputAsJson` (default: true)
- `llmCompactor.compressStackFrames` (default: true)
- `llmCompactor.showFixTargets` (default: true)
- `llmCompactor.showRecentChanges` (default: true)
- `llmCompactor.includePackages` (comma-separated list of packages to preserve in traces)

The only output on stdout will be the structured JSON summary.
