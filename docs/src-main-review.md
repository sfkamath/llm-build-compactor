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
> in `ParserUtils.resolveFrameSource`).

---

## Findings

| # | Sev | Module / File | Location | Issue | Suggested fix |
|---|-----|---------------|----------|-------|---------------|
| 6 | 🟠 Med | gradle-plugin · `LlmCompactorPlugin` | javadoc `:80-129` | **Stale javadoc defaults.** `getShowFixTargets` doc says "default: false" (actual `true`); `getShowSlowTests` doc says "default: true" (actual `false`). Contradicts `CompactorConfig`. | Correct the javadoc to match `CompactorConfig.DEFAULT_*`. |
| 8 | 🟡 Low | core · `SummaryWriter` | `toHumanReadable` `:251` + `:304` | **Double stack-trace stripping.** `normalize()` already applies `StackTraceCompressor.stripPackagePrefixes` to each error; the render loop applies it again. Redundant work, no behavior change. | Drop the second `stripPackagePrefixes` call in the render loop. |
| 9 | 🟡 Low | core · `SummaryWriter` | `LOGGER_PATTERN` `:85` | **Dead field.** Only referenced in commented-out code (`:118`). | Remove the field and the commented line. |
| 12 | 🟡 Low | gradle-plugin · `LlmCompactorPlugin` | `propertyMissing` `:147-153` | **Warning printed to `System.err`** which is redirected to null when enabled → the "unknown property" warning is silently lost in the common case. | Emit via the Gradle logger (quiet level) instead of `System.err`. |
| 13 | 🟡 Low | core · `CompactorConfig` | `resolved()` `:48-108` | **Hand-written 15-method decorator.** Verbose anonymous reimplementation; every new config field must be added in 3+ places (interface, `DefaultCompactorConfig`, `resolved()`, both consumers). Maintenance smell, not a bug. | Optional: generate the overlay (e.g. a small delegating base or apply preset onto a copied builder) to cut duplication. |
| 14 | 🟡 Low | extension · `BuildOutputSpy` / maven-plugin · `LlmCompactMojo` | `LlmCompactMojo.java:127` | **Mojo builds config from raw `@Parameter enabled`** (`.enabled(enabled)`) rather than the project-property-resolved `enabledValue` used for the gate. Harmless after the early-return, but inconsistent. | Build config from the same resolved value used for gating. |
| 15 | 🟡 Low | core / gradle-plugin | `GradleParser.java:56`, `CompletionService.java:292-294`, `BuildOutputSuppressor.java:188` | **Broad silent catches.** Several `catch (Exception)`/`catch (IOException) { // ignore }` swallow errors with no diagnostic. Acceptable for resilience but hides real failures. | Log at `debug`/`FINE` before swallowing, consistent with `TestCountLogger`. |
| 16 | 🟡 Low | docs · `gradle-design.md` | `:41,:104` | **Doc/code drift.** Design doc states a "500ms delay" for late stream restoration; code uses `2000ms`. Section numbering under "How Suppression Is Achieved" also skips "1." | Sync the doc to the actual delay (and reconcile #1's redesign if the timing changes). |
| 22 | 🟢 Feat | **core** (new `JacocoCoverageReader`) + extension + gradle-plugin | new — *inspired by* `fix/generic-build-errors:19b762d`, **re-mechanised + relocated to core** | **Salvage (re-designed): JaCoCo coverage-failure footer, shared by both build systems.** When a coverage-gated build goes red (or passes), emit `[JACOCO_COVERAGE] Coverage: N% (...) - required: M%` so an LLM sees *why* inline. **Do NOT port the branch's `.exec`-reflection verbatim** — it is the inferior mechanism (bytecode-probe level, forced reflection, needs `org.jacoco.core` dep). **Absent from HEAD.** Confirmed non-overlapping with jvm-coverage-mcp (whose XML+StAX read is strictly richer). | **Primary mechanism = read `jacoco.xml` via DOM** (match `parser/XmlParserUtils`, *not* StAX) (`target/site/jacoco/jacoco.xml` / `jacoco-merged.xml`; Gradle `build/reports/jacoco/test/jacocoTestReport.xml`). Line/method/branch level, **no jacoco dep, no reflection**. Normally present by the time `jacoco:check` fails (report runs first). **Split:** (a) **core** — `JacocoCoverageReader` (DOM parse of `<report>`/`<counter>` → `CoverageResult`), plus optional **rough** `.exec` fallback (reflection, probe-level only) for when the report mojo hasn't run. (b) **extension** — locate XML/exec + read minimums from `MavenProject` pom check-rules (`Xpp3Dom`). (c) **gradle-plugin** — locate XML/exec + read minimums from the Jacoco DSL. **Design call first:** thread `CoverageResult` into `BuildSummary` via `SummaryBuilder` (nullable field + new telescoping ctor — see salvage §2 hazard note), not the branch's `REAL_OUT` side-channel. See [`abandoned-branch-salvage.md`](abandoned-branch-salvage.md) §2. |
| 23 | 🟢 Feat | core · `CompilationErrorExtractor` | verify HEAD vs `fix/generic-build-errors:35f2a10` | **Salvage check: modernizer-style errors.** Confirm HEAD extracts `[ERROR] <file>:<line>: <msg>` (modernizer/plugin) lines. **Confirmed: HEAD's generic `pattern` (`:18`) already matches these** (the single-colon path was previously untested). Test added but **`@Disabled`** (parked, not a priority): `CompilationErrorExtractorTest.shouldExtractModernizerErrors`. **NB: distinct from #25** — #23 is extraction coverage, #25 is the compile-error footer *duplication/leak*. | Re-enable the parked test if modernizer support becomes a priority. |
| 26 | 🟡 Low | core · `CompactorDefaults` (test gap) | follow-up to shipped #24 | **No unit test for the sealed null stream.** The `print`/`println`/`write(byte[])` overrides ship untested (graph flagged `nullPrintStream`/`print`/`println` untested). A direct zero-byte assertion needs an injectable sink — `nullPrintStream()` discards to an internal `OutputStream`. | Add a package-private overload (or test helper) that wraps a caller-supplied `OutputStream`; assert `println(String)`/`print(Object)` write zero bytes. Haiku. |
| 25 | 🔴 High | gradle-plugin · suppression (regression) | `GradleBuildOutputTests.java:58-67` (test gap); should-have-fixed in `7289eb7`, `38eb919` | **Suppression only works for test failures, not compile failures.** Observed in the field (Haiku run on another project): on a `compileJava` failure the compactor emits the JSON summary correctly **but the raw Gradle failure footer still leaks to the console** — `FAILURE: Build failed with an exception.` / `* What went wrong:` / `Execution failed for task ':compileJava'` / `BUILD FAILED in 1s` all print alongside the summary. So the build error is double-reported (compacted + raw). **NB: distinct from #23** (which is extraction coverage, not this leak). `testNoGradleFailureSummary` asserts `doesNotContain("* What went wrong:")` etc., but it runs the **`test`** task only — it never exercises the `compileJava` path, so the regression passes CI. | Reproduce: add a `testNoGradleFailureSummary`-style assertion against the `gradle-compile-error-project` / `compileJava` task (the same project `testCompilationErrors` already uses). Then make compile-failure footers suppress the same way test-failure footers do. |

---

## Phased Execution Plan (for delegation to Sonnet)

Each phase is independently shippable. Verify with `mvn clean verify -Pquality` (llm-compactor active) after each.

### Phase 1 — Correctness / stream-safety (blockers)
Goal: no path can leave a JVM/daemon with null or wrong std streams.
*(#1, #2 shipped — removed. Remaining:)*
1. **#16** Update `gradle-design.md` delay (500ms → actual) once #1's timing is final.

### Phase 2 — Config correctness & consistency
Goal: defaults agree across core, Gradle, Maven.
4. **#6** Fix stale javadoc defaults in `LlmCompactorPlugin`.
6. **#14** Mojo: use resolved enabled value for config.
   - Verify: a test asserting Gradle and Maven produce identical `showFixTargets`/`showSlowTests` defaults for an unconfigured project.

### Phase 3 — Side-effect & robustness hardening *(shipped: #3, #7, #10, #11)*
10. **#15** Add debug/FINE logging before broad catches.
   - Verify: cross-version `CrossVersionTest` + summary-suppression tests stay green.

### Phase 4 — Cleanup / maintainability (low risk) *(shipped: #4)*
11. **#8** Remove double `stripPackagePrefixes` in `SummaryWriter`.
12. **#9** Delete dead `LOGGER_PATTERN`.
13. **#12** Route `propertyMissing` warning through Gradle logger.
14. **#13** (Optional) De-duplicate `CompactorConfig.resolved()` overlay.
   - Verify: `SummaryWriterTest`, `GradleParser`/`SurefireParser` parser tests unchanged in output.

---

## Addendum B — `CompactorConfig` / `DefaultCompactorConfig` / `CompactorDefaults` optimisation

| # | Sev | Target | Issue | Suggested fix |
|---|-----|--------|-------|---------------|
| 18 | 🟡 Low | `CompactorConfig` ↔ `DefaultCompactorConfig` | `DEFAULT_*` constants on the interface are each restated as `@Builder.Default` on the impl. Acceptable Lombok pattern, but two lists to keep in sync. | Leave as-is unless touched; note for awareness. |
| 19 | 🟡 Low | `CompactorDefaults` | Grab-bag of two unrelated statics: `resolveEnabled` (config policy) and `nullPrintStream` (IO util). Low cohesion. | Optional: move `nullPrintStream` to a `core/util` IO helper; keep `resolveEnabled` with config. |

---

## Addendum C — Documentation tasks

| # | Sev | Target | Task |
|---|-----|--------|------|
| 21 | 🟡 Low | `README` | README does **not** index the `docs/` folder. Delegated agent should: enumerate every file under `docs/`, add a "Documentation" index section to `README` linking each with a one-line description, and verify no doc is orphaned. Agent does its own discovery (do not assume the current file list). |

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
| 5 | Inverted `getOrElse` defaults | T | **Haiku** | medium | One-line constant swaps — *or* deleted entirely by Addendum A. |
| 6 | Stale Gradle javadoc defaults | T | **Haiku** | medium | Doc-only constant correction. |
| 8 | Drop double `stripPackagePrefixes` | T | **Haiku** | medium | Remove one redundant call; verify SummaryWriterTest. |
| 9 | Delete dead `LOGGER_PATTERN` | T | **Haiku** | medium | Remove field + commented line. |
| 12 | `propertyMissing` → Gradle logger | T | **Haiku** | medium | Swap `System.err.println` for logger call. |
| 14 | Mojo uses resolved enabled value | T | **Haiku** | medium | One-line source swap. |
| 15 | Log before broad catches | S | **Haiku** | high | Several call-sites; add debug/FINE, no logic change. |
| 19 | Split `CompactorDefaults` cohesion | S | **Haiku** | medium | Move `nullPrintStream` to IO util; mechanical. |
| 21 | README docs index | S | **Haiku** | medium | Discovery + mechanical index section. Should now also index `abandoned-branch-salvage.md`. |
| 22 | JaCoCo coverage footer (core + Maven, then Gradle) | M | **Sonnet** | high | Re-mechanised (XML/DOM, not the branch's `.exec` reflection); core/plugin split + `BuildSummary` ctor ripple + tests. Core+Maven first, Gradle follow-up. See salvage §2. |
| 23 | Verify/port modernizer-error extraction | T | **Haiku** | medium | Run branch test against HEAD; port regex only if it fails. |
| 25 | Compile-failure footer leaks to console | M | **Opus** (Fable) | high | Suppression regression — works for `test`, not `compileJava`. Needs the failing-footer suppression path + a `compileJava` IT closing the `testNoGradleFailureSummary` gap. |

**Suggested batching for delegation:**
- **Opus batch** (stream safety): #1 + #2 + #24 **shipped**. Remaining: **#25** (compile-failure footer) — same suppression machinery, but the leak is via Gradle's ERROR-level renderer, not `System.out`; needs a `compileJava` repro IT.
- **Sonnet batch 1** (config dedup): **shipped** (#5, #17, #20, Addendum A).
- **Sonnet batch 2** (suppression robustness): **shipped** (#3, #7, #10, #11, #4).
- **Sonnet batch 3** (salvage feature): **#22** core+Maven, then a self-contained Gradle follow-up PR. Standalone — no dependency on other batches.
- **Haiku batch** (mechanical cleanup): #6, #8, #9, #12, #14, #15, #19, #21, **#23**.

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
