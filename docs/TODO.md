# LLM Build Compactor TODO Log
Status: ⚠️ in progress  
Date: 2026-03-27

## 🔴 Critical (Must Have)
- ⬜ **Java version specific tests**: Add tests for Lombok, Groovy, Spock with Gradle.
- ⬜ **Test count caching bug**: Investigate if test count (331) is cached in the JSON report.

## 🟡 Important (Should Have)
- ⬜ **Gradle plugin sandboxing**: Restrict gradle module changes to project rather than global changes

## 🟢 Nice to Have (Can Wait)
- ⬜ **IDE integration**: IntelliJ/Eclipse plugin for inline error display.

---
## 📝 Completed Milestones
- ✅ **Stacktrace compression include**: Added stackFrameWhitelist and stackFrameBlacklist options to override defaults.
- ✅ **Stack trace parsing fix**: Fixed to use first project frame instead of last frame.
- ✅ **Test logs**: Fixed to preserve class names, fixed enabled=false flag.
- ✅ **Fatal compilation errors**: Added extraction of "Fatal error compiling" errors with ANSI code stripping.
- ✅ **Backward compatibility**: Added propertyMissing to Gradle extension for unknown properties.
- ✅ **Module rename**: Renamed `core` to `llm-build-compactor-core` across all POMs, Gradle files, and documentation.
- ✅ **Gradle composite build**: Implemented composite build for Gradle plugin with core module.
- ✅ **Maven extension**: Built core extension for early lifecycle hooks.
- ✅ **Stacktrace compression**: Implemented intelligent stacktrace filtering.
- ✅ **Git diff extraction**: Added context-aware git diff parsing.
