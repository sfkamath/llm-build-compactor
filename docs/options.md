# Usage Options

The `llm-compactor-maven-plugin` provides several configuration options to customize the build summary output. These can be configured in your project's `pom.xml` or passed as system properties.

## Maven Plugin Parameters

| Property | Default | Description |
|----------|---------|-------------|
| `llmCompactor.enabled` | `true` | Toggle the entire plugin on/off. |
| `llmCompactor.outputPath` | `target/llm-summary.json` | Path where the JSON summary is saved. |
| `llmCompactor.outputAsJson` | `true` | If true, prints pretty-printed JSON to stdout. If false, prints human-readable summary. |
| `llmCompactor.compressStackFrames` | `true` | Filter out non-project stack frames (JUnit, Maven, Java internals). |
| `llmCompactor.showFixTargets` | `true` | Include suggested files and snippets for fixing errors. |
| `llmCompactor.showRecentChanges` | `true` | Include list of files changed in the last few commits. |

## Core Extension Settings

The same system properties (prefixed with `llmCompactor.`) also control the behavior of the Core Extension (`maven-extension`).

## Example: Silence mode

For completely silent builds with only the LLM summary output:

```bash
mvn verify -DllmCompactor.outputAsJson=true
```
