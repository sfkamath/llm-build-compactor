# `src/main` Branch Review — `fix/generic-build-errors-v2`

Scope: production code under `src/main` across all modules, diffed vs `origin/main`.
Excluded: Lombok/boilerplate, generated/getter changes, test fixtures (`testbed/*`, `Bad.java`),
and all `src/test` / `integration-tests` code.

Modules reviewed:
- `llm-build-compactor-core`
- `llm-build-compactor-gradle-plugin`
- `llm-build-compactor-extension` (Maven EventSpy)
- `llm-build-compactor-maven-plugin`

> **This file is a working TODO, not a record.** Shipped items are deleted, not ticked — the code +
> its javadoc + `docs/*-design.md` are the documentation of what landed. See the Development Guide's
> "Documentation Convention". Items removed so far: **#1, #2, #24** (stream-safety batch; rationale
> now lives in javadoc on `CompletionService`/`BuildOutputSpy`/`CompactorDefaults`). **#5, #17, #20,
> Addendum A** (config dedup: `DefaultCompactorConfig` is now `Serializable` + `toBuilder`;
> `resolved()` is a 6-line `toBuilder()` overlay on `DefaultCompactorConfig`; `Params` collapsed to
> single `Property<DefaultCompactorConfig> getConfig()`). **#3, #4, #7, #10, #11** (suppression
> robustness: `autoInstall` removed, `neuter` narrowed to `progressLogger`/`logger` by name,
> `setFork(false)` removed, redundant `doFirst` dropped, Gradle/Surefire source resolver unified
> in `ParserUtils.resolveFrameSource`). **#6, #8, #9, #12, #14, #15, #19, #21, #23** (Haiku batch:
> stale javadoc defaults corrected, dead `LOGGER_PATTERN` removed, double `stripPackagePrefixes`
> dropped, `propertyMissing` routed via Gradle logger, Mojo uses resolved enabled, FINE logging
> added to broad catches, `nullPrintStream` moved to `IoUtils`, README docs index added, modernizer
> violation to `test-project-maven` still needed before `shouldExtractModernizerErrors` can be
> un-parked — see finding #23). **#26** (stale `test-results/` replay: `emit()` re-parsed
> `build/test-results/` ungated, so any non-Test invocation — compile-only, **or a failure like a
> wrong task path / config error** — replayed a previous run's cached JSON and masked the real
> outcome; field-found on `micronaut-data`. Fixed by gating `GradleParser.parse` on a
> `minLastModifiedMillis` = build `sessionStartTime`; rationale in javadoc on `GradleParser.parse`
> and the `CompletionService.emit()` comment. Regression guard: `GradleStaleTestResultsTests` +
> `gradle-stale-results-project`).

---

## Findings

| # | Sev | Module / File | Location | Issue | Suggested fix |
|---|-----|---------------|----------|-------|---------------|
| 13 | 🟡 Low | core · `CompactorConfig` | `resolved()` `:48-108` | **Hand-written 15-method decorator.** Verbose anonymous reimplementation; every new config field must be added in 3+ places (interface, `DefaultCompactorConfig`, `resolved()`, both consumers). Maintenance smell, not a bug. | Optional: generate the overlay (e.g. a small delegating base or apply preset onto a copied builder) to cut duplication. |
| 16 | 🟡 Low | docs · `gradle-design.md` | `:41,:104` | **Doc/code drift.** Design doc states a "500ms delay" for late stream restoration; code uses `2000ms`. Section numbering under "How Suppression Is Achieved" also skips "1." | Sync the doc to the actual delay (and reconcile #1's redesign if the timing changes). |
| 23 | 🟢 Feat | core · `CompilationErrorExtractor` | `CompilationErrorExtractorTest.shouldExtractModernizerErrors` (`@Disabled`) | **IT-level gap for modernizer-style extraction.** Unit parsing confirmed correct (hardcoded log strings pass). Full verification requires: add `modernizer-maven-plugin` to `test-project-maven`, introduce an `Optional.orElseThrow()`-style violation, write an IT asserting the extracted `BuildError` matches. | Add modernizer plugin + violation to `test-project-maven`; write IT; re-enable `shouldExtractModernizerErrors`. |
| 22 | 🟢 Feat | **core** (new `JacocoCoverageReader`) + extension + gradle-plugin | new — *inspired by* `fix/generic-build-errors:19b762d`, **re-mechanised + relocated to core** | **Salvage (re-designed): JaCoCo coverage-failure footer, shared by both build systems.** When a coverage-gated build goes red (or passes), emit `[JACOCO_COVERAGE] Coverage: N% (...) - required: M%` so an LLM sees *why* inline. **Do NOT port the branch's `.exec`-reflection verbatim** — it is the inferior mechanism (bytecode-probe level, forced reflection, needs `org.jacoco.core` dep). **Absent from HEAD.** Confirmed non-overlapping with jvm-coverage-mcp (whose XML+StAX read is strictly richer). | **Primary mechanism = read `jacoco.xml` via DOM** (match `parser/XmlParserUtils`, *not* StAX) (`target/site/jacoco/jacoco.xml` / `jacoco-merged.xml`; Gradle `build/reports/jacoco/test/jacocoTestReport.xml`). Line/method/branch level, **no jacoco dep, no reflection**. Normally present by the time `jacoco:check` fails (report runs first). **Split:** (a) **core** — `JacocoCoverageReader` (DOM parse of `<report>`/`<counter>` → `CoverageResult`), plus optional **rough** `.exec` fallback (reflection, probe-level only) for when the report mojo hasn't run. (b) **extension** — locate XML/exec + read minimums from `MavenProject` pom check-rules (`Xpp3Dom`). (c) **gradle-plugin** — locate XML/exec + read minimums from the Jacoco DSL. **Design call first:** thread `CoverageResult` into `BuildSummary` via `SummaryBuilder` (nullable field + new telescoping ctor — see salvage §2 hazard note), not the branch's `REAL_OUT` side-channel. See [`abandoned-branch-salvage.md`](abandoned-branch-salvage.md) §2. |
| 25 | 🔴 High | gradle-plugin · suppression (regression) | `GradleBuildOutputTests.java:58-67` (test gap); should-have-fixed in `7289eb7`, `38eb919` | **Suppression only works for test failures, not compile failures.** Observed in the field (Haiku run on another project): on a `compileJava` failure the compactor emits the JSON summary correctly **but the raw Gradle failure footer still leaks to the console** — `FAILURE: Build failed with an exception.` / `* What went wrong:` / `Execution failed for task ':compileJava'` / `BUILD FAILED in 1s` all print alongside the summary. So the build error is double-reported (compacted + raw). **NB: distinct from #23** (which is extraction coverage, not this leak). `testNoGradleFailureSummary` asserts `doesNotContain("* What went wrong:")` etc., but it runs the **`test`** task only — it never exercises the `compileJava` path, so the regression passes CI. | Reproduce: add a `testNoGradleFailureSummary`-style assertion against the `gradle-compile-error-project` / `compileJava` task (the same project `testCompilationErrors` already uses). Then make compile-failure footers suppress the same way test-failure footers do. |

---

## Phased Execution Plan (for delegation to Sonnet)

Each phase is independently shippable. Verify with `mvn clean verify -Pquality` (llm-compactor active) after each.

### Phase 1 — Correctness / stream-safety (blockers)
Goal: no path can leave a JVM/daemon with null or wrong std streams.
*(#1, #2 shipped — removed. Remaining:)*
1. **#16** Update `gradle-design.md` delay (500ms → actual) once #1's timing is final.

### Phase 2 — Config correctness & consistency *(shipped: #6, #14)*
Goal: defaults agree across core, Gradle, Maven.

### Phase 3 — Side-effect & robustness hardening *(shipped: #3, #7, #10, #11, #15)*

### Phase 4 — Cleanup / maintainability *(shipped: #4, #8, #9, #12, #19)*
4. **#13** (Optional) De-duplicate `CompactorConfig.resolved()` overlay.

---

## Addendum B — `CompactorConfig` / `DefaultCompactorConfig` / `CompactorDefaults` optimisation

| # | Sev | Target | Issue | Suggested fix |
|---|-----|--------|-------|---------------|
| 18 | 🟡 Low | `CompactorConfig` ↔ `DefaultCompactorConfig` | `DEFAULT_*` constants on the interface are each restated as `@Builder.Default` on the impl. Acceptable Lombok pattern, but two lists to keep in sync. | Leave as-is unless touched; note for awareness. |

---

---

## Addendum D — `gradle-design.md` accuracy (corrected in this pass)

Audited the design doc against the source now in context. Corrected the following drift directly in `docs/gradle-design.md`:

| Was | Now | Source of truth |
|-----|-----|-----------------|
| "500ms delay" (×2) for late stream restoration | "2000ms (2s)" | `CompletionService.java:238` |
| Restoration registers a fallback `TestListener` that prints `FAILED` to `System.out` | configures `testLogging.getQuiet()` to emit FAILED + exceptions/causes | `BuildOutputSuppressor.java:108-116` |
| Reads config via Gradle's `findProperty()` API | `ProviderFactory.gradleProperty()` → `systemProperty()` | `LlmCompactorPlugin.java:251-281` |
| "How Suppression Is Achieved" subsections numbered 2–7 | renumbered 1–6 | cosmetic |

Otherwise accurate: one-stage plugin model, `OperationCompletionListener`/`TaskFailureResult`
capture, serializable `BuildServiceParameters`, root-once registration, XML test-result parsing,
quiet-logger summary emission, and dedup/high-signal filtering all match the code.
Not documented (omission, not error): `setFork(false)` and GString compiler-arg normalization in
`applyQuietJavaCompileOptions`.

---

## Addendum E — Complexity & agent assignment

Complexity: **T**rivial (mechanical, 1 file) · **S**mall (1-2 files, clear) · **M**edium (multi-file
or design judgment) · **L**arge (cross-cutting / concurrency).

Agent rationale: route correctness/concurrency to the strongest reasoner (Opus, or Fable when
speed matters), multi-file refactors with judgment to Sonnet, mechanical/well-specified edits to
Haiku. Effort = the model's reasoning-effort setting.

| # | Item | Cx | Agent | Effort | Why |
|---|------|----|-------|--------|-----|
| 22 | JaCoCo coverage footer (core + Maven, then Gradle) | M | **Sonnet** | high | Re-mechanised (XML/DOM, not the branch's `.exec` reflection); core/plugin split + `BuildSummary` ctor ripple + tests. Core+Maven first, Gradle follow-up. See salvage §2. |
| 25 | Compile-failure footer leaks to console | M | **Opus** (Fable) | high | Suppression regression — works for `test`, not `compileJava`. Needs the failing-footer suppression path + a `compileJava` IT closing the `testNoGradleFailureSummary` gap. |

**Suggested batching for delegation:**
- **Opus batch** (stream safety): #1 + #2 + #24 **shipped**. Remaining: **#25** (compile-failure footer) — same suppression machinery, but the leak is via Gradle's ERROR-level renderer, not `System.out`; needs a `compileJava` repro IT.
- **Sonnet batch 1** (config dedup): **shipped** (#5, #17, #20, Addendum A).
- **Sonnet batch 2** (suppression robustness): **shipped** (#3, #7, #10, #11, #4).
- **Sonnet batch 3** (salvage feature): **#22** core+Maven, then a self-contained Gradle follow-up PR. Standalone — no dependency on other batches.
- **Haiku batch** (mechanical cleanup): **shipped** (#6, #8, #9, #12, #14, #15, #19, #21). **#23** remains open — IT-level completion needed.

> **Sequencing note:** the abandoned-branch salvage (#22–23) is independent of the remaining review findings and can run in parallel. `feat/gradle-flow-api-variants` (Addendum F head-start) is **post-merge / post-Phase-1-2 only** — see `abandoned-branch-salvage.md` §4.

---

## Addendum F — Event-driven stream restoration (remaining hardening beyond #1)

The 2s timer in `CompletionService.restoreStreamsLate` is a guess, not a signal. **#1 shipped the
boundary-driven half** — `captureOriginals()` cancels any `pendingRestore` and the next
`LlmCompactorPlugin.apply()` is treated as the "event"; the null stream is identified by `==`
against the shared `nullSentinel`, not by value. Remaining, unshipped hardening:

- **No usable "output finished" event.** Gradle's final failure footer is rendered by
  launcher/output infrastructure *after* `BuildService.close()`, outside any plugin-observable
  event. `BuildListener.buildFinished` / `Gradle.buildFinished {}` are deprecated and fire too
  early; `OperationCompletionListener` only sees task events. `close()` is already the last hook —
  hence the residual timer.
- Add `Runtime.getRuntime().addShutdownHook(...)` to restore on daemon teardown (a real terminal
  event) so a crash mid-window can't leave streams nulled.
- Optional (raises min Gradle): Build Flow API (`FlowScope` / `BuildWorkResultProvider`, 8+) as an
  end-of-build-work signal — but it still orders before footer rendering, so it supplements rather
  than replaces the delay. Ties into the #25 footer-leak investigation.

**Head-start exists.** The abandoned `feat/gradle-flow-api-variants` branch already prototyped the
version-gated `Legacy`/`Modern` lifecycle listener split (`gradle/lifecycle/BuildLifecycleListener`
+ `BuildLifecycleFactory`) that step 3 would otherwise reinvent. It is a *placeholder* (self-labeled
"NOT WORKING") — **read it, don't merge it**, and only *after* this branch + Phase 1–2 land. Full
sequencing rationale in [`abandoned-branch-salvage.md`](abandoned-branch-salvage.md) §4.

---

## Coverage — what this review did and did NOT read

This review is **not exhaustive**. Direct source reads covered the highest-risk / most-changed
`src/main` files; the remaining changed files were not opened. Agents should not assume the
unreviewed set is clean.

**Reviewed (read in full):**
- gradle-plugin: all 6 (`LlmCompactorPlugin`, `CompletionService`, `BuildOutputSuppressor`, `TestCountLogger`, `GradlePropertiesInstaller`, `BuildSummaryEmitter`)
- core: `SummaryWriter`, `StackTraceCompressor`, `CompilationErrorExtractor`, `parser/GradleParser`, `parser/SurefireParser`, `ModePreset`, `CompactorConfig`, `DefaultCompactorConfig`, `CompactorDefaults`
- extension: `BuildOutputSpy`, `PropertyResolver`
- maven-plugin: `LlmCompactMojo`

**Changed but NOT yet reviewed:**
- core: `BuildError`, `BuildSummary`, `FixTarget`, `SlowTest`, `SummaryBuilder`, `PackageDiscoverer`, `parser/ParserUtils`, `parser/TestLogReader`, `parser/TestResult`, `parser/TestResultAggregator`, `parser/XmlParserUtils`, `extract/FixTargetGenerator`, `git/GitDiffExtractor`, `snippet/CodeSnippetExtractor`, `context/AgentContextWriter`, `context/ModuleDetector`, `context/RepairContextBuilder`, `util/AnsiStripper`, `util/ConfigAccessor`
- extension: `OutputConfig`, `PackageScanner`, `TestResultCollector`

Note: `BuildSummary` (+179/−... ) and the `SummaryBuilder` / `TestResultAggregator` /
`XmlParserUtils` / `TestLogReader` cluster are new or heavily changed and carry the
shared parse/aggregate logic — they are the most worthwhile next read.

---

## Context notes (provenance, for continuity)
- `Params extends BuildServiceParameters` first appeared in `9f126a7` ("refactor: decompose LlmCompactorPlugin god class"); late stream restoration in `7289eb7`; one-stage unification in `38eb919`. Branch under review: `fix/generic-build-errors-v2` (vs `origin/main`).
- `detect_changes` (graph) exceeded the token cap and was not consumed; all findings are from direct reads.

---

## Notes
- No security issues found in `src/main`.
- Largest single risks are the two stream-restoration bugs (#1, #2; shipped). Addendum A config dedup (#5, #17, #20) also shipped.
- `detect_changes` graph output exceeded the token cap; this review is from direct source reads of the changed `src/main` files.
