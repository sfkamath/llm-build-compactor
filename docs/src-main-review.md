# `src/main` Branch Review — `fix/generic-build-errors-v2`

Scope: production code under `src/main` across all modules, diffed vs `origin/main`.
Excluded: Lombok/boilerplate, generated/getter changes, test fixtures (`testbed/*`, `Bad.java`),
and all `src/test` / `integration-tests` code.

Modules reviewed:
- `llm-build-compactor-core`
- `llm-build-compactor-gradle-plugin`
- `llm-build-compactor-extension` (Maven EventSpy)
- `llm-build-compactor-maven-plugin`

---

## Findings

| # | Sev | Module / File | Location | Issue | Suggested fix |
|---|-----|---------------|----------|-------|---------------|
| 1 | 🔴 High | gradle-plugin · `CompletionService` + `LlmCompactorPlugin` | `CompletionService.java:182-242`, `LlmCompactorPlugin.java:242-243` | **Daemon stream race.** `originalOut/originalErr` are `static`. Plugin captures `System.out` at `apply()` time; restore is scheduled 2000ms after `close()` on a daemon thread. In a reused Gradle daemon, a second build starting within the restore window (a) captures the *null* stream as "original", and (b) the pending restore can clobber the new build's redirect. Net effect: daemon stdout/stderr can be permanently nulled or restored to the wrong stream. | Make capture idempotent: only set `originalOut/originalErr` if currently `null` AND not already the null sentinel. Cancel/skip restore if a new build has re-captured. Prefer restoring synchronously at a deterministic point over a timed background thread. |
| 2 | 🔴 High | extension · `BuildOutputSpy` | `close()` `BuildOutputSpy.java:91-97` | **`System.setOut(null)` in pass-through.** When `shouldPassThrough()` is true, `originalOut/originalErr` are never assigned, but `close()` unconditionally calls `System.setOut(originalOut)` / `setErr(originalErr)` → installs `null` streams, breaking stdout/stderr for the rest of the JVM. | Guard restore: only restore when `originalOut != null` (i.e. streams were actually captured/suppressed). |
| 3 | 🟠 Med | gradle-plugin · `GradlePropertiesInstaller` | `autoInstall` called from `LlmCompactorPlugin.java:232-234` | **Silent mutation of tracked source file.** Every enabled `apply()` writes `org.gradle.logging.level=quiet` into the user's `gradle.properties`. Idempotent, but auto-editing a version-controlled file as a side effect of running a build is surprising and hard to attribute. | Gate behind explicit opt-in (the `installLlmCompactor` task already exists) or, at minimum, log at lifecycle level when the block is first written. |
| 4 | 🟠 Med | core · `parser/GradleParser` vs `parser/SurefireParser` | `GradleParser.java:94-123`, `SurefireParser.java:152-222` | **Duplicated, divergent source/line extraction.** Two hand-rolled stack-frame → (file,line) parsers with different behavior: Gradle returns a bare filename; Surefire resolves to a real `src/...` path. Divergence is a latent correctness/maintenance hazard. | Extract one shared frame-resolver (in `ParserUtils` or a new helper) used by both parsers; unify the "resolve to source path" behavior. |
| 5 | 🟠 Med | gradle-plugin · `CompletionService` | `toConfig` `CompletionService.java:359-376` | **Fallback defaults inverted vs canonical.** `getShowFixTargets().getOrElse(false)` but `DEFAULT_SHOW_FIX_TARGETS=true`; `getShowSlowTests().getOrElse(true)` but `DEFAULT_SHOW_SLOW_TESTS=false`. Masked today because params are always populated from conventions, but the literals are a latent bug if a param is ever absent. | Replace literals with the `CompactorConfig.DEFAULT_*` constants. |
| 6 | 🟠 Med | gradle-plugin · `LlmCompactorPlugin` | javadoc `:80-129` | **Stale javadoc defaults.** `getShowFixTargets` doc says "default: false" (actual `true`); `getShowSlowTests` doc says "default: true" (actual `false`). Contradicts `CompactorConfig`. | Correct the javadoc to match `CompactorConfig.DEFAULT_*`. |
| 7 | 🟠 Med | gradle-plugin · `TestCountLogger` | `neuter` `:45-87` | **Broad reflective field replacement.** Replaces *all* non-collection interface fields (across the full superclass chain) with no-op proxies. Cross-version fragility: a future Gradle field could be neutered unintentionally, silently corrupting reporting. Behavior is documented but inherently brittle. | Narrow targeting to the known fields (`progressLogger`, `logger`) by type/name where possible; keep the FINE diagnostics. Add a cross-version guard test (already partly covered). |
| 8 | 🟡 Low | core · `SummaryWriter` | `toHumanReadable` `:251` + `:304` | **Double stack-trace stripping.** `normalize()` already applies `StackTraceCompressor.stripPackagePrefixes` to each error; the render loop applies it again. Redundant work, no behavior change. | Drop the second `stripPackagePrefixes` call in the render loop. |
| 9 | 🟡 Low | core · `SummaryWriter` | `LOGGER_PATTERN` `:85` | **Dead field.** Only referenced in commented-out code (`:118`). | Remove the field and the commented line. |
| 10 | 🟡 Low | gradle-plugin · `BuildOutputSuppressor` | `applyQuietJavaCompileOptions` `:136` | **Forces `setFork(false)` globally** on every `JavaCompile`. Can change memory behavior / risk OOM on large multi-module builds that rely on forked compilation. | Confirm intent; consider not overriding `fork` unless needed for the warning suppression. |
| 11 | 🟡 Low | gradle-plugin · `BuildOutputSuppressor` | `configureEach` `:64-82` | **Redundant double application.** Quiet logging / compile options are applied in `configureEach` *and* re-applied in `doFirst`. Likely defensive but doubles work and obscures intent. | Keep one; document why if `doFirst` is genuinely required for ordering. |
| 12 | 🟡 Low | gradle-plugin · `LlmCompactorPlugin` | `propertyMissing` `:147-153` | **Warning printed to `System.err`** which is redirected to null when enabled → the "unknown property" warning is silently lost in the common case. | Emit via the Gradle logger (quiet level) instead of `System.err`. |
| 13 | 🟡 Low | core · `CompactorConfig` | `resolved()` `:48-108` | **Hand-written 15-method decorator.** Verbose anonymous reimplementation; every new config field must be added in 3+ places (interface, `DefaultCompactorConfig`, `resolved()`, both consumers). Maintenance smell, not a bug. | Optional: generate the overlay (e.g. a small delegating base or apply preset onto a copied builder) to cut duplication. |
| 14 | 🟡 Low | extension · `BuildOutputSpy` / maven-plugin · `LlmCompactMojo` | `LlmCompactMojo.java:127` | **Mojo builds config from raw `@Parameter enabled`** (`.enabled(enabled)`) rather than the project-property-resolved `enabledValue` used for the gate. Harmless after the early-return, but inconsistent. | Build config from the same resolved value used for gating. |
| 15 | 🟡 Low | core / gradle-plugin | `GradleParser.java:56`, `CompletionService.java:292-294`, `BuildOutputSuppressor.java:188` | **Broad silent catches.** Several `catch (Exception)`/`catch (IOException) { // ignore }` swallow errors with no diagnostic. Acceptable for resilience but hides real failures. | Log at `debug`/`FINE` before swallowing, consistent with `TestCountLogger`. |
| 16 | 🟡 Low | docs · `gradle-design.md` | `:41,:104` | **Doc/code drift.** Design doc states a "500ms delay" for late stream restoration; code uses `2000ms`. Section numbering under "How Suppression Is Achieved" also skips "1." | Sync the doc to the actual delay (and reconcile #1's redesign if the timing changes). |
| 22 | 🟢 Feat | **core** (new `JacocoCoverageReader`) + extension + gradle-plugin | new — *inspired by* `fix/generic-build-errors:19b762d`, **re-mechanised + relocated to core** | **Salvage (re-designed): JaCoCo coverage-failure footer, shared by both build systems.** When a coverage-gated build goes red (or passes), emit `[JACOCO_COVERAGE] Coverage: N% (...) - required: M%` so an LLM sees *why* inline. **Do NOT port the branch's `.exec`-reflection verbatim** — it is the inferior mechanism (bytecode-probe level, forced reflection, needs `org.jacoco.core` dep). **Absent from HEAD.** Confirmed non-overlapping with jvm-coverage-mcp (whose XML+StAX read is strictly richer). | **Primary mechanism = read `jacoco.xml` via DOM** (match `parser/XmlParserUtils`, *not* StAX) (`target/site/jacoco/jacoco.xml` / `jacoco-merged.xml`; Gradle `build/reports/jacoco/test/jacocoTestReport.xml`). Line/method/branch level, **no jacoco dep, no reflection**. Normally present by the time `jacoco:check` fails (report runs first). **Split:** (a) **core** — `JacocoCoverageReader` (DOM parse of `<report>`/`<counter>` → `CoverageResult`), plus optional **rough** `.exec` fallback (reflection, probe-level only) for when the report mojo hasn't run. (b) **extension** — locate XML/exec + read minimums from `MavenProject` pom check-rules (`Xpp3Dom`). (c) **gradle-plugin** — locate XML/exec + read minimums from the Jacoco DSL. **Design call first:** thread `CoverageResult` into `BuildSummary` via `SummaryBuilder` (nullable field + new telescoping ctor — see salvage §2 hazard note), not the branch's `REAL_OUT` side-channel. See [`abandoned-branch-salvage.md`](abandoned-branch-salvage.md) §2. |
| 23 | 🟢 Feat | core · `CompilationErrorExtractor` | verify HEAD vs `fix/generic-build-errors:35f2a10` | **Salvage check: modernizer-style errors.** Confirm HEAD extracts `[ERROR] <file>:<line>: <msg>` (modernizer/plugin) lines. If missing, port the parser branch + `shouldExtractModernizerErrors` test. | Run/port the test; add regex only if it fails. |
| 24 | 🟢 Feat | core · `CompactorDefaults` + extension null-stream | ties to #1/#2 | **Salvage: seal the null `PrintStream`.** `jul-placeholder-rabbit-hole-wip` proved the null stream overrides only `write(...)`; `println(String)`/`print(Object)` can leak via encoder paths. Override `print(String/Object)` + `println(String/Object)` + `write(byte[])` too. | Add overrides in `CompactorDefaults.nullPrintStream` and the extension's `nullPrintStream()`; add a test asserting `println(String)` emits zero bytes. See `abandoned-branch-salvage.md` §3. |

---

## Phased Execution Plan (for delegation to Sonnet)

Each phase is independently shippable. Verify with `mvn clean verify -Pquality` (llm-compactor active) after each.

### Phase 1 — Correctness / stream-safety (blockers)
Goal: no path can leave a JVM/daemon with null or wrong std streams.
1. **#2** `BuildOutputSpy.close()` — guard restore on `originalOut != null`.
   - Verify: pass-through run (disabled / interactive goal) leaves `System.out` intact; add/extend an extension IT asserting stdout works post-build.
2. **#1** `CompletionService` static-stream race — idempotent capture (never overwrite a live capture / null sentinel) and safe restore.
   - Verify: back-to-back Gradle builds in one daemon (`GradleOptionTests`) still print summaries and restore streams; no permanent null.
3. **#16** Update `gradle-design.md` delay (500ms → actual) once #1's timing is final.

### Phase 2 — Config correctness & consistency
Goal: defaults agree across core, Gradle, Maven.
4. **#5** Replace inverted `getOrElse` literals in `CompletionService.toConfig` with `CompactorConfig.DEFAULT_*`.
5. **#6** Fix stale javadoc defaults in `LlmCompactorPlugin`.
6. **#14** Mojo: use resolved enabled value for config.
   - Verify: a test asserting Gradle and Maven produce identical `showFixTargets`/`showSlowTests` defaults for an unconfigured project.

### Phase 3 — Side-effect & robustness hardening
7. **#3** Gate or loudly log `autoInstall` writes to `gradle.properties`.
8. **#7** Narrow `TestCountLogger` field targeting; keep FINE diagnostics + cross-version test.
9. **#10 / #11** Reconsider global `setFork(false)`; drop redundant `doFirst` re-application.
10. **#15** Add debug/FINE logging before broad catches.
   - Verify: cross-version `CrossVersionTest` + summary-suppression tests stay green.

### Phase 4 — Cleanup / maintainability (low risk)
11. **#8** Remove double `stripPackagePrefixes` in `SummaryWriter`.
12. **#9** Delete dead `LOGGER_PATTERN`.
13. **#4** Extract shared stack-frame → source resolver for `GradleParser`/`SurefireParser`.
14. **#12** Route `propertyMissing` warning through Gradle logger.
15. **#13** (Optional) De-duplicate `CompactorConfig.resolved()` overlay.
   - Verify: `SummaryWriterTest`, `GradleParser`/`SurefireParser` parser tests unchanged in output.

---

## Addendum A — `CompletionService.Params` repetition (investigation)

**Why `Params extends BuildServiceParameters` exists.** It is mandatory, not accidental. Gradle's
`BuildService<P>` contract requires `P extends BuildServiceParameters`, and the parameters must be
Gradle-*managed* types (`Property`/`ListProperty`) so the service is lazily configurable and
serializable for the Configuration Cache (exactly what `gradle-design.md` calls "fully
serializable … through `BuildServiceParameters`"). A plain `CompactorConfig` cannot be handed to a
`BuildService` directly. Introduced when the plugin god-class was decomposed (`9f126a7`),
unchanged since.

**There is a *second* mandatory `Property` surface, for a different reason.** `Params` is not the
only place the ~13 fields are mirrored as Gradle `Property` getters — `LlmCompactorPlugin`'s nested
`LlmCompactorExtension` (`:39-145`) does too. This is **also mandatory, but not the same
requirement**: it is the **DSL extension** (`project.getExtensions().create("llmCompactor", …)`,
`:159`), and it is genuinely used — `llmCompactor { … }` blocks exist in
`test-project-gradle/build.gradle:48`, the compile-error IT project, and the plugin's own
`build.gradle:56`. Gradle requires `Property`-typed getters there for lazy DSL config +
`.convention()`. So `Extension` (DSL surface) and `Params` (BuildService-serialization surface) are
two distinct, independently-mandated mirrors of the same fields — **neither is deletable by fiat**.

**The repetition is therefore quintuple.** Each of the ~13 config fields is restated in five places:

| Place | File | Mandatory? |
|-------|------|------------|
| Canonical definition | `CompactorConfig` / `DefaultCompactorConfig` | yes (the model) |
| DSL `Property<T>` getter | `LlmCompactorPlugin.LlmCompactorExtension` (`:39-145`) | yes (DSL extension — used) |
| BuildService `Property<T>` getter | `CompletionService.Params` (`:43-176`) | reducible (see below) |
| Extension→Params `.set(...)` bridge | `BuildSummaryEmitter` (`:49-62`) | reducible |
| Rebuild back into a config | `CompletionService.toConfig` (`:359-376`) | reducible |

Adding one config flag means editing all five. This is the single largest maintenance smell in the
Gradle module. **The two genuinely-irreducible surfaces are the `CompactorConfig` model and the
`LlmCompactorExtension` DSL** — the other three (Params bag, emitter bridge, `toConfig`) all
collapse.

**Optimisation.** Collapse the three reducible surfaces into one config-bearing property:
- Make `DefaultCompactorConfig implements Serializable` (it is only primitives + `List<String>`, so trivially serializable / Configuration-Cache friendly).
- Replace the ~13 config getters in `Params` with a single `Property<DefaultCompactorConfig> getConfig()`. Keep only the genuinely runtime-scoped getters (`sessionStartTime`, `rootDir`, `buildDir`, `allBuildDirs`, `allSourceDirs`).
- Delete `CompletionService.toConfig` entirely; call `getConfig().get().resolved()`.
- Collapse `BuildSummaryEmitter`'s ~13 `.set()` calls to one `params.getConfig().set(buildConfig(extension))`.

Net: five edit-sites per field → **two** (the `CompactorConfig` model + the `LlmCompactorExtension`
DSL, both irreducible). This also removes finding **#5** (the inverted `getOrElse` fallbacks vanish
with `toConfig`).

**Rejected alternative — merge Extension *into* Params.** One could make
`LlmCompactorExtension extends BuildServiceParameters` and use the extension type as the params
type, deleting one interface. Don't: Gradle instantiates the BuildService's params as a *separate
instance* from the extension, so the field-by-field copy in `BuildSummaryEmitter` would remain, and
it couples the user-facing DSL to the BuildService contract. The single-`getConfig()` route above
removes the copy *and* keeps the surfaces decoupled — strictly better.

---

## Addendum B — `CompactorConfig` / `DefaultCompactorConfig` / `CompactorDefaults` optimisation

| # | Sev | Target | Issue | Suggested fix |
|---|-----|--------|-------|---------------|
| 17 | 🟠 Med | `CompactorConfig.resolved()` (`:48-108`) | 15-method hand-written decorator that re-delegates every getter just to overlay `ModePreset` on 3 fields. Verbose; every new field must be added here too. | Add `@Builder(toBuilder = true)` to `DefaultCompactorConfig`; implement `resolved()` as `toBuilder().outputAsJson(preset.overrideOutputAsJson(outputAsJson())).showFixTargets(...).showFailedTestLogs(...).build()`. ~60 lines → ~6. (Requires `resolved()` to move to `DefaultCompactorConfig`, or a small static helper.) |
| 18 | 🟡 Low | `CompactorConfig` ↔ `DefaultCompactorConfig` | `DEFAULT_*` constants on the interface are each restated as `@Builder.Default` on the impl. Acceptable Lombok pattern, but two lists to keep in sync. | Leave as-is unless touched; note for awareness. |
| 19 | 🟡 Low | `CompactorDefaults` | Grab-bag of two unrelated statics: `resolveEnabled` (config policy) and `nullPrintStream` (IO util). Low cohesion. | Optional: move `nullPrintStream` to a `core/util` IO helper; keep `resolveEnabled` with config. |
| 20 | 🟡 Low | `DefaultCompactorConfig` | Not `Serializable` — blocks the Addendum A optimisation. | Implement `Serializable` (enables single-property BuildService config). |

Items 17 + 20 are the high-value ones and dovetail with Addendum A.

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
| 1 | Daemon static-stream race | L | **Opus** (Fable if speed) | high | Daemon lifecycle + concurrency; wrong fix re-breaks streams. Needs careful reasoning + IT design. |
| 2 | `BuildOutputSpy.close()` null streams | S | **Opus** (Fable) | medium | Tiny diff but subtle pass-through reasoning; pair with #1 for shared stream-safety IT. |
| 3 | `autoInstall` gates source-file write | M | **Sonnet** | high | Behavioral/UX decision + task wiring; needs opt-in design. |
| 4 | Unify Gradle/Surefire frame resolver | M | **Sonnet** | high | Cross-file extraction, must preserve both parsers' output (regression-prone). |
| 5 | Inverted `getOrElse` defaults | T | **Haiku** | medium | One-line constant swaps — *or* deleted entirely by Addendum A. |
| 6 | Stale Gradle javadoc defaults | T | **Haiku** | medium | Doc-only constant correction. |
| 7 | Narrow `TestCountLogger` reflection | M | **Sonnet** | high | Reflection + cross-version (8.x/9.x) correctness; keep CrossVersionTest green. |
| 8 | Drop double `stripPackagePrefixes` | T | **Haiku** | medium | Remove one redundant call; verify SummaryWriterTest. |
| 9 | Delete dead `LOGGER_PATTERN` | T | **Haiku** | medium | Remove field + commented line. |
| 10 | Reconsider global `setFork(false)` | S | **Sonnet** | medium | Needs build/perf judgment + verification, not mechanical. |
| 11 | Redundant `doFirst` re-apply | S | **Sonnet** | medium | Must confirm ordering need before removing; behavioral risk. |
| 12 | `propertyMissing` → Gradle logger | T | **Haiku** | medium | Swap `System.err.println` for logger call. |
| 14 | Mojo uses resolved enabled value | T | **Haiku** | medium | One-line source swap. |
| 15 | Log before broad catches | S | **Haiku** | high | Several call-sites; add debug/FINE, no logic change. |
| 17 | `resolved()` → `toBuilder()` overlay | M | **Sonnet** | high | Restructures decorator; touches CompactorConfig contract. |
| 19 | Split `CompactorDefaults` cohesion | S | **Haiku** | medium | Move `nullPrintStream` to IO util; mechanical. |
| 20 | `DefaultCompactorConfig implements Serializable` | S | **Sonnet** | high | Enabler for Addendum A; verify Config-Cache serialization. |
| A | Single-property BuildService config | L | **Sonnet** | high | Touches Params/emitter/CompletionService/DefaultCompactorConfig + the `LlmCompactorExtension` DSL mirror together; subsumes #5. Sequence after #20. Quintuple→2 surfaces (see Addendum A). |
| 21 | README docs index | S | **Haiku** | medium | Discovery + mechanical index section. Should now also index `abandoned-branch-salvage.md`. |
| 22 | JaCoCo coverage footer (core + Maven, then Gradle) | M | **Sonnet** | high | Re-mechanised (XML/DOM, not the branch's `.exec` reflection); core/plugin split + `BuildSummary` ctor ripple + tests. Core+Maven first, Gradle follow-up. See salvage §2. |
| 23 | Verify/port modernizer-error extraction | T | **Haiku** | medium | Run branch test against HEAD; port regex only if it fails. |
| 24 | Seal null `PrintStream` (`print`/`println` overrides) | S | **Sonnet** | medium | Concurrency-adjacent; ties to #1/#2 stream-safety. Add zero-byte `println` test. |

**Suggested batching for delegation:**
- **Opus batch** (stream safety): #1 + #2 + **#24** together, one IT covering all three. (#24 seals the null stream the others restore — same blast radius.)
- **Sonnet batch 1** (config dedup): #20 → A → #17 (+ #5 falls out). One PR.
- **Sonnet batch 2** (suppression robustness): #3, #7, #10, #11, #4.
- **Sonnet batch 3** (salvage feature): **#22** core+Maven, then a self-contained Gradle follow-up PR. Standalone — no dependency on other batches.
- **Haiku batch** (mechanical cleanup): #6, #8, #9, #12, #14, #15, #19, #21, **#23**.

> **Sequencing note:** the abandoned-branch salvage (#22–24) is independent of the review findings (#1–21) and can run in parallel. `feat/gradle-flow-api-variants` (Addendum F head-start) is **post-merge / post-Phase-1-2 only** — see `abandoned-branch-salvage.md` §4.

---

## Addendum F — Event-driven stream restoration (refines #1)

The 2s timer in `CompletionService.restoreStreamsLate` is a guess, not a signal. Findings:

- **No usable "output finished" event.** Gradle's final failure footer is rendered by
  launcher/output infrastructure *after* `BuildService.close()`, outside any plugin-observable
  event. `BuildListener.buildFinished` / `Gradle.buildFinished {}` are deprecated and fire too
  early; `OperationCompletionListener` only sees task events. `close()` is already the last hook —
  hence the timer.
- **Preferred fix — boundary-driven, not time-driven** (also resolves the #1 race):
  1. Keep `scheduler` + the `ScheduledFuture` in static fields. On the next build's
     `LlmCompactorPlugin.apply()`, if `System.out` is still the null sentinel, restore immediately
     and `future.cancel(false)` any pending restore. The "event" is the next plugin apply.
  2. Add `Runtime.getRuntime().addShutdownHook(...)` to restore on daemon teardown (a real
     terminal event).
  3. Optional (raises min Gradle): Build Flow API (`FlowScope` / `BuildWorkResultProvider`, 8+)
     as an end-of-build-work signal — but it still orders before footer rendering, so it
     supplements rather than replaces the delay.
- Identify the null stream reliably: compare against a stored sentinel reference (the
  `nullPrintStream()` instance), not `== originalOut`.

Route this with #1 to the Opus batch.

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
- Largest single risks are the two stream-restoration bugs (#1, #2); the largest maintainability win is Addendum A (+ #17/#20).
- `detect_changes` graph output exceeded the token cap; this review is from direct source reads of the changed `src/main` files.
