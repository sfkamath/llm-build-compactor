Problem Restatement: Silent Maven Build for LLM Output

    Goal

    When running any Maven command (mvn verify, mvn test -Dtest=MyTest, mvn spotless:apply, etc.), the only output should be:
     - target/llm-summary.json written to disk
     - JSON printed to stdout

    No other output should appear:
     - No Maven banners ([INFO] Scanning for projects...)
     - No plugin output ([INFO] Compiling 4 source files...)
     - No test output ([INFO] Tests run: 5, Failures: 1...)
     - No application logs (Logback DEBUG, INFO statements from user code)

    Why This Is Hard

    Maven's logging is configured before plugins execute. By the time our plugin runs (in the VERIFY phase), all output has already been printed.

    ---

    Options Tried

    Option 1: Fork Maven Internally
    Approach: Plugin runs mvn clean verify as a subprocess with -q -B flags, captures output.

    Result: ❌ Rejected
     - Doubles build time (runs build twice)
     - Complex goal passthrough logic needed
     - Breaks incremental builds

    ---

    Option 2: Maven Extension for Logging Suppression
    Approach: Auto-create .mvn/extensions.xml that loads our extension before any plugin. Extension configures Logback/SLF4J to ERROR level.

    Result: ⚠️ Partially Working
     - Extension loads too late to affect Maven's own SLF4J Simple Logger
     - Can suppress application Logback logs (but requires code changes)
     - Adds complexity, another module to maintain

    Current Status: Extension module exists but doesn't effectively suppress output.

    ---

    Option 3: maven.config Auto-Creation (Current)
    Approach: Plugin auto-creates .mvn/maven.config with --quiet --batch-mode and SLF4J properties.

    Result: ⚠️ Partially Working
     - ✅ Suppresses Maven framework output ([INFO] banners, plugin messages)
     - ✅ Suppresses SLF4J-based plugin logging
     - ❌ Does NOT suppress application Logback output (test project's own logs)
     - ❌ Requires re-run after first build (config not in place for first build)

    Current Output:

     22:50:25.513 [main] DEBUG io.llmcompactor.testbed.Order - Creating order...
     22:50:25.514 [main] INFO  i.l.testbed.PaymentService - Processing refund...
     [ERROR] Tests run: 5, Failures: 1...  ← From Surefire, not Maven
     {"status":"FAILED",...}  ← Our JSON

    ---

    Option 4: Document Manual Flags
    Approach: Don't create any config. Document that users should run mvn -q -B verify.

    Result: ❌ Rejected
     - Violates "zero configuration" requirement
     - Users must remember flags
     - Doesn't work for mvn spotless:apply etc.

    ---

    Option 5: Extension Wraps System.out
    Approach: Extension wraps System.out and System.err with filtering streams that swallow everything except our JSON.

    Result: ⚠️ Not Fully Tested
     - Could work but is invasive
     - May break other plugins that rely on console output
     - Risk of swallowing our JSON too

    ---

    Option 6: Hybrid (maven.config + Extension)
    Approach:
     - maven.config suppresses Maven framework output
     - Extension suppresses application Logback output

    Result: ⚠️ Partially Implemented
     - maven.config works
     - Extension needs implementation for Logback suppression

    ---

    Current State


    ┌─────────────────────────────────────┬─────────────┬─────────────────────────────────┐
    │ Output Type                         │ Suppressed? │ How                             │
    ├─────────────────────────────────────┼─────────────┼─────────────────────────────────┤
    │ Maven banners ([INFO] Scanning...)  │ ✅ Yes      │ maven.config --quiet            │
    │ Plugin output ([INFO] Compiling...) │ ✅ Yes      │ maven.config SLF4J properties   │
    │ Surefire summary (Tests run: 5...)  │ ⚠️ Partial  │ maven.config (ERROR level only) │
    │ Application Logback logs            │ ❌ No       │ Not configured                  │
    │ Our JSON output                     │ ✅ Yes      │ Direct System.out.print()       │
    └─────────────────────────────────────┴─────────────┴─────────────────────────────────┘
