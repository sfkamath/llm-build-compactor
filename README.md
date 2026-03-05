# LLM Build Compactor

A lightweight tool that extracts **actionable build diagnostics** from Maven and Gradle runs.

Instead of sending thousands of log lines to an LLM, it produces:

- build summaries
- failing tests
- compilation errors
- fix targets
- minimal code snippets

Output format:

build/llm-summary.json

This dramatically reduces context usage for AI coding agents.
