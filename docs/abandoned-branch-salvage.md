# Abandoned-Branch Salvage Review

**Date:** 2026-06-10
**Current branch:** `fix/generic-build-errors-v2` (HEAD)
**Branches assessed:** `fix/generic-build-errors`, `feat/gradle-flow-api-variants`, `jul-placeholder-rabbit-hole-wip`
**Common fork point:** `25154dc` (2026-03-27) — all three branched here; HEAD is a separate variant from the same root.

> Companion to [`src-main-review.md`](src-main-review.md). The JaCoCo salvage is filed there as **Task #22**; the Flow-API timing question is answered in **§4** below and cross-referenced from `src-main-review.md` Addendum F.

---

## TL;DR

| Branch | Tip | Unique substance | Verdict |
|--------|-----|------------------|---------|
| `fix/generic-build-errors` | 2026-04-03 | Build-failure detection (**already in HEAD**), JaCoCo coverage extraction (**missing**), modernizer-error test | **Salvage JaCoCo only** → Task #22 |
| `feat/gradle-flow-api-variants` | 2026-04-01 | Gradle Flow-API lifecycle abstraction (version-gated Legacy/Modern listeners) | **Keep as design reference**, not a merge — see §4 timing |
| `jul-placeholder-rabbit-hole-wip` | 2026-05-19 | Logger-framework reflective probing of SLF4J/JUL internals | **Don't merge.** But it surfaced one real bug (§3) and one real conclusion worth keeping |

None of the three branches' commits are cherry-picked into HEAD (`git cherry HEAD <branch>` = all `+`). Where HEAD "has" their work, it was **re-implemented**, not merged.

---

## 1. Build-failure-extraction — the stated top goal — is DONE in HEAD

This was the headline of `fix/generic-build-errors` (commits `d1ebfc1`, `35f2a10`). Cross-checked against HEAD `BuildOutputSpy.java`:

| Capability | Abandoned branch | HEAD status |
|-----------|------------------|-------------|
| `buildFailed` flag set on any mojo failure | `handleMojoFailed` sets `buildFailed=true` for non-compiler plugins | **Present** — `BuildOutputSpy.java:184` sets it unconditionally on `MojoFailed` |
| Session-result fallback for missed messages | `if (!buildFailed && session.getResult().hasExceptions()) buildFailed=true` | **Present** — `BuildOutputSpy.java:240` (identical logic) |
| Non-compiler failure → generic error wrap | `createGenericCompilationError(...)` fallback | **Present, cleaner** — `extractOrWrap(ee, output)` + `CompilationErrorExtractor.extractOrWrap` |
| `FAILED` status when `buildFailed` even with no errors | `allErrors.isEmpty() && !buildFailed ? "SUCCESS" : "FAILED"` | **Present** — `SummaryBuilder.withBuildFailed(buildFailed)` |
| Multi-line compiler error detail (`symbol:`/`location:`) | added in branch | **Present** — `CompilationErrorExtractor.java:91` |
| Modernizer-style `[ERROR] file:line: msg` extraction | added with test | **Verify** — see Task #23 below |

### Multi-module angle (explicitly asked)
Two mechanisms make HEAD multi-module-correct already:
- **Per-module events:** `MojoFailed`/`MojoSucceeded` fire once per reactor module; `ee.getProject()` scopes each to its own module dir. Errors accumulate across modules into one summary.
- **Session-wide safety net:** `session.getResult().hasExceptions()` (`BuildOutputSpy.java:240`) catches a failure in *any* reactor module even when per-mojo message extraction returns empty — the reactor-level backstop. This is the key multi-module guarantee and it is already in.

**Conclusion:** No salvage needed for build-failure detection. The abandoned branch's approach was the prototype; HEAD is the refactored, better version. Only **JaCoCo** rode along in that branch and never got carried over.

---

## 2. JaCoCo coverage surfacing — the real salvage (→ Task #22)

`fix/generic-build-errors` commit `19b762d` added reflection-based coverage extraction to the **Maven** `BuildOutputSpy`. Confirmed **absent** from HEAD (`git grep jacoco` on HEAD `BuildOutputSpy.java` = no match; only an unrelated pom property `jacoco.coverage.minimum`).

### What it does (source: `fix/generic-build-errors:BuildOutputSpy.java`)
- New `MojoSucceeded` event case + `handleMojoSucceeded(ee)`.
- In **both** `handleMojoFailed` and `handleMojoSucceeded`: detect the jacoco `check` goal
  (`org.jacoco` / `jacoco-maven-plugin` / goal `check`) and call `extractJacocoCoverageFromReport(ee)`.
- `extractJacocoCoverageFromReport(ee)`:
  - resolves `<module>/target/jacoco.exec` (falls back to `jacoco-merged.exec`),
  - reads it **reflectively** via `org.jacoco.core.data.ExecutionDataReader` +
    a `Proxy` `IExecutionDataVisitor` counting `getProbes()` hits/misses — no compile-time jacoco dependency on the hot path,
  - reads required minimums from the module pom via `readConfiguredMinimums(project)`
    (walks `jacoco-maven-plugin` → executions(goal=check) → `rules/rule/limits/limit/{counter,minimum}`),
  - emits `[JACOCO_COVERAGE] Coverage: N% (covered/total <counter>s covered) - required: M%`.
- Captured lines collected in `capturedLines` and flushed to `REAL_OUT` after the summary.

### The salvage value is the *idea*, not the *mechanism* (jvm-coverage-mcp cross-check)

The compactor's job here: a one-line **`[JACOCO_COVERAGE]` footer** at the moment a coverage-gated
build goes red — tell the LLM *why* inline, without it having to query coverage separately. That
intent is sound and **in-scope**. The branch's *implementation* of it is not the one to keep.

Reviewed against `~/Developer/jvm-coverage-mcp` (jacoco-focused). Conclusions:

1. **Nothing from the branch method belongs in jvm-coverage-mcp.** That tool reads the **XML report**
   via StAX — line/method/branch/instruction level. The branch's `.exec` probe read is
   bytecode-level and **strictly poorer**. No overlap, no migration.
2. **The reflection is a forced workaround, not a design choice.** A Maven *extension* can't add
   `org.jacoco.core` as a compile-time dep without polluting the extension classloader → hence the
   `ExecutionDataReader` reflection. **That constraint does not justify keeping `.exec` as the
   primary path** — the XML report is already on disk and needs no jacoco dep at all.
3. **`.exec` gives an inferior input.** Probes fired ≠ lines/branches covered. The XML is JaCoCo's
   own line/method/branch rollup. Prefer it.

### Re-mechanised design: XML-first, `.exec` as rough fallback only

| Source | Level | Dep | When |
|--------|-------|-----|------|
| **`jacoco.xml`** (primary) | line/method/branch | **none** | after `jacoco:report` / `jacocoTestReport` — normally already run when `check` fails |
| `.exec` (fallback) | probe, **rough only** | `org.jacoco.core` reflection | only if the report mojo hasn't run yet; label "rough signal, not method-level" |

> **Parse with DOM, not StAX.** Core already parses XML via `DocumentBuilder` in
> `parser/XmlParserUtils` — match that house style. (StAX is jvm-coverage-mcp's choice and is
> irrelevant here.) jacoco.xml shape: read the `<counter type="LINE"|"BRANCH"|...">` elements
> (top-level `<report>` counters give the aggregate `missed`/`covered`).

Paths: Maven `target/site/jacoco/jacoco.xml` (+`target/jacoco-merged.xml`); Gradle
`build/reports/jacoco/test/jacocoTestReport.xml`.

### Relocate to **core** — both plugins must benefit
The branch implemented this Maven-only, inside `extension/BuildOutputSpy`. Wrong for the target
architecture: Gradle gets nothing. Three separable concerns:

| Concern | Build-system-specific? | Home |
|---------|------------------------|------|
| Parse `jacoco.xml` (StAX) → `CoverageResult`; optional rough `.exec` fallback | **No** | **`llm-build-compactor-core`** — new `JacocoCoverageReader` |
| Locate the XML/exec file | Yes (Maven `target/site/...` ; Gradle `build/reports/...`) | each plugin |
| Read required minimums | Yes (Maven pom `jacoco-maven-plugin` check rules via `Xpp3Dom`; Gradle Jacoco DSL) | each plugin |

### Port cost (multi-file, multi-module — delegated task, not a one-liner)
1. **core** — new `JacocoCoverageReader`: StAX parse of `jacoco.xml` → `CoverageResult`
   (covered/total/percentage per counter). Optional rough `.exec` reader behind reflection. **No
   compile-time jacoco dep for the XML path.**
2. **extension** `BuildOutputSpy.java` — locate report/exec + read minimums from `MavenProject`
   check-rules, call core on jacoco `check` `MojoFailed`/`MojoSucceeded`. **Adapt to HEAD shape**
   (`SummaryBuilder`/`OutputConfig`).
3. **gradle-plugin** — locate report/exec + read minimums from the Jacoco DSL, call core.
4. `dependency-check-suppressions.xml` — only needed if the `.exec` fallback keeps `org.jacoco.core`.
5. Test — core unit test for `JacocoCoverageReader` against a fixture `jacoco.xml`; per-plugin IT for
   the footer. (Branch's modernizer test is separate — Task #23.)

### Threshold caveat (Gradle)
`readConfiguredMinimums` is the one mildly-interesting branch piece (surfacing "72%, required 80%"),
but it is **Maven-pom-only**. Gradle thresholds live in the `build.gradle` Jacoco DSL — not portable
without the per-plugin split above. Don't try to share the threshold reader across build systems.

### Output integration (don't blind-port)
The branch appends `[JACOCO_COVERAGE]` to `REAL_OUT` *after* the summary — a side-channel.
**Prefer** threading `CoverageResult` into `BuildSummary` via `SummaryBuilder` so it renders inside
the summary (human + JSON) consistently. Decide before porting. Flagged in Task #22.

**`BuildSummary` constructor ripple (implementation hazard).** `BuildSummary` uses **telescoping
positional constructors** (not a Lombok builder); the 9-arg ctor is the widest. Adding `coverage`:
- add a **nullable** `CoverageResult coverage` field (annotate `@JsonInclude(NON_NULL)`),
- add **one** new widest constructor taking it; have the existing 9-arg ctor delegate with `null`,
- in `SummaryBuilder`: `withCoverage(CoverageResult)` + pass it in `build()`,
- in `SummaryWriter`: render one line in `toHumanReadable` (e.g. `Coverage: 72% (300/400 LINE) -
  required: 80%`) and include the field in JSON. Keep all existing callers compiling untouched.

---

## 3. `jul-placeholder-rabbit-hole-wip` — not cover, but contents (re-judged)

Commit `6272f4b` "lots of debugging around jul and which logger". Full diff is **one file** (`extension/BuildOutputSpy.java`, +182). Read in full. It is **diagnostic instrumentation**, not a feature:
- Reflectively dumps every static field of `org.slf4j.impl.SimpleLogger`, `SimpleLoggerConfiguration`, `OutputChoice`; probes classpath for Logback / Log4j2 / Jetty / JUL handlers; prints identity hashes of the null stream.
- `suppressJul()` / `restoreJul()` exist but are **commented out** at call sites (`// suppressJul();`, `// restoreJul();`) — abandoned mid-experiment.
- Replaces `resetSlf4j()` with `resetSlf4j(PrintStream)` that *force-injects* `nullPrint` into SLF4J's `OutputChoice.targetPrintStream` via reflection, then prints `SHOULD-BE-INVISIBLE` to test it.

### What's worth keeping (the "more in there")
1. **A real, still-open bug it proves.** The branch overrides `write(byte[])`, `write(byte[],int,int)`, **and** `print(String)`/`println(String)`/`print(Object)`/`println(Object)` on the null `PrintStream`. HEAD's null stream (and `CompactorDefaults.nullPrintStream`) override only `write(...)`. `PrintStream.println(String)` does **not** always route through `write(int)` — encoders/`BufferedWriter` paths can leak. **This corroborates `src-main-review.md` #2 and #1's stream-safety theme**: the null stream is incompletely sealed. → filed as **Task #24**.
2. **A confirmed conclusion** (commit body: *"get feeling about showFailedTestLogs was right"*) — the SLF4J `targetPrintStream` is a **separate** reference from `System.out`; redirecting `System.setOut` alone does not silence SLF4J SimpleLogger, because `OutputChoice` caches its own stream at init. Any future "why is SLF4J still printing" investigation should start here instead of re-deriving it. → captured as **Exploratory note E-1** below.

Everything else (the field dumps, classpath probes, JUL toggles) is throwaway. **Delete the branch after Task #24 is filed** — its value is now captured here.

---

## 4. `feat/gradle-flow-api-variants` — before fixes or after merge? (explicit question)

Commit `4d03407` self-labels *"NOT WORKING - placeholder"*. It introduces a real architecture though:
- `gradle/lifecycle/BuildLifecycleListener` (interface) + `BuildLifecycleFactory`
- `LegacyBuildLifecycleListener` (pre-Flow, `buildFinished`-style) vs `ModernBuildLifecycleListener` (Gradle 8 Flow API)
- `LlmCompactorExtension` (new) and a **~150-line shrink** of `LlmCompactorPlugin` (153 → core delegating to the factory)
- CI: `gradlew-gradle8` / `gradlew-gradle85` wrappers to test both lifecycles; `docs/TODO.md` (+39).

### Recommendation: **AFTER current branch is merged and stable — and BEFORE/INSTEAD-OF re-deriving Addendum F.**

Reasoning:
- **Not before the `src-main-review.md` fixes.** Those fixes (esp. #1 stream-restoration race, #2 null-stream restore) are **correctness blockers** on the *current* one-stage architecture. Flow-API is a structural change that would rebase those fixes onto a moving target. Stabilise first.
- **It is the intended end-state for Addendum F.** `src-main-review.md` Addendum F recommends boundary-driven restoration and lists the Flow API as the clean end-of-build signal. This branch is a **head-start on exactly that** — the `Modern`/`Legacy` split is the version-gating Addendum F would otherwise reinvent. Whoever implements #1 properly should **read this branch first**, not merge it.
- **Sequencing:** (1) merge current branch, (2) land `src-main-review.md` Phase 1–2 fixes, (3) re-open Flow-API as the vehicle for the *real* #1 fix (event-driven restoration), porting from this branch rather than the placeholder commit. Treat the branch as a spike to harvest, not a PR to revive.

→ This answer is cross-referenced from `src-main-review.md` Addendum F.

---

## 5. Exploratory tasks (for Sonnet / Haiku)

| ID | Task | Source refs | Agent / effort |
|----|------|-------------|----------------|
| **#22** | JaCoCo coverage-failure footer (see §2). **Re-mechanise, don't port:** `JacocoCoverageReader` in **core** reading `jacoco.xml` (StAX), per-plugin file/threshold location, `CoverageResult` into `BuildSummary`. `.exec` reflection only as rough fallback. | *idea from* `fix/generic-build-errors:19b762d` (`extractJacocoCoverageFromReport`/`readConfiguredMinimums`); mechanism from `~/Developer/jvm-coverage-mcp` (XML+StAX) | **Sonnet, high** (cross-module + design calls) |
| **#23** | Verify HEAD extracts modernizer-style `[ERROR] <file>:<line>: <msg>`; if not, port the parser branch + test. | `fix/generic-build-errors:35f2a10` `CompilationErrorExtractorTest.shouldExtractModernizerErrors`; HEAD `CompilationErrorExtractor.java` | **Haiku, medium** (1 test + maybe 1 regex) |
| **#24** | Seal the null `PrintStream`: override `print(String/Object)` + `println(String/Object)` + `write(byte[])` in `CompactorDefaults.nullPrintStream` and the extension's `nullPrintStream()`. Add a test that asserts `println(String)` produces no bytes. | `jul-placeholder-rabbit-hole-wip:6272f4b` null-stream overrides; HEAD `CompactorDefaults.nullPrintStream`; ties to `src-main-review.md` #1/#2 | **Sonnet, medium** (concurrency-adjacent) |
| **E-1** | (Note, not a task) SLF4J SimpleLogger caches its own `OutputChoice.targetPrintStream` at init — independent of `System.out`. If SLF4J output ever leaks under suppression, that field (not `System.setOut`) is the lever. Don't re-investigate from scratch. | `jul-placeholder-rabbit-hole-wip:6272f4b` `resetSlf4j(PrintStream)` | — |

---

## 6. Branch disposition

- `fix/generic-build-errors` — **delete after Task #22 + #23 land.** All other content already in HEAD.
- `jul-placeholder-rabbit-hole-wip` — **delete after Task #24 filed** (done here). Pure diagnostics otherwise.
- `feat/gradle-flow-api-variants` — **keep until the Flow-API/Addendum-F work begins**, then harvest and delete. Tag it so it isn't mistaken for active work.

*(Deletion is the user's call — listed as recommendation, not action.)*
