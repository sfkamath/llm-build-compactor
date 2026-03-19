# Usage Options

The compactor uses the same `llmCompactor.*` property family across:

- the Maven core extension
- the Maven fallback Mojo
- the Gradle plugin

These values can be supplied through:

- Maven system properties
- Maven `pom.xml` properties or plugin configuration
- Gradle build configuration
- Gradle system properties

## Common Options

| Property | Default | Description |
|----------|---------|-------------|
| `llmCompactor.enabled` | `true` | Toggle the compactor on or off. |
| `llmCompactor.outputAsJson` | `true` | Emit JSON when `true`; emit human-readable text when `false`. |
| `llmCompactor.compressStackFrames` | `true` | Filter framework-heavy stack traces down to useful project frames. |
| `llmCompactor.showFixTargets` | `true` | Include suggested file/line fix targets and snippets when available. |
| `llmCompactor.showRecentChanges` | `true` | Include recently changed Git files in the summary. |
| `llmCompactor.includePackages` | empty | Comma-separated packages to keep in compressed stack traces even if they look framework-like. |
| `llmCompactor.showDuration` | `true` | Show duration for individual failing tests in human-readable output. |
| `llmCompactor.showTotalDuration` | `false` | Include total build duration in the summary when available. |
| `llmCompactor.showDurationReport` | `false` | Include test duration percentile data when available. |
| `llmCompactor.outputPath` | varies by integration | Write the summary to a file as well as emitting it to the console. |

## Integration Notes

### Maven

- Preferred path: the core extension installed via `.mvn/extensions.xml`
- Fallback path: the `compact` Mojo
- Typical file output: `target/llm-summary.json`

### Gradle

- Uses the Gradle plugin plus the companion init script bootstrap
- The plugin emits a final compact summary at build end
- File output is optional through `llmCompactor.outputPath`

## Example

Human-readable output with stack-trace compression enabled:

```bash
mvn verify -DllmCompactor.outputAsJson=false
./gradlew build -DllmCompactor.outputAsJson=false
```
