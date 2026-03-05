# Test Project for LLM Build Compactor

A self-contained test project used to test the LLM Build Compactor plugin.

## Purpose

- Test compilation error extraction
- Test test failure parsing (Surefire unit tests)
- Test integration test parsing (Failsafe)
- Test stack trace compression
- Test fix target generation
- Test Lombok integration
- Test Logback logging

## Structure

```
test-project/
├── pom.xml                          # Maven config with plugin configured
├── src/
│   ├── main/java/.../testbed/
│   │   ├── PaymentService.java      # Main service with logging
│   │   ├── Order.java               # Domain class with Lombok
│   │   ├── Refund.java              # Domain class with @Value
│   │   └── OrderProcessor.java      # Processor with @RequiredArgsConstructor
│   ├── test/java/.../testbed/
│   │   ├── PaymentServiceTest.java  # Unit tests (some pass)
│   │   ├── OrderServiceTest.java    # Tests with intentional failures
│   │   └── StackTraceTest.java      # Tests that produce stack traces
│   ├── it/java/.../testbed/
│   │   ├── PaymentIT.java           # Integration tests (Failsafe)
│   │   └── OrderProcessorIT.java    # Integration tests (Failsafe)
│   └── test/resources/
│       └── logback-test.xml         # Logback configuration
└── README.md
```

## Features

### Lombok
- `@Value` for immutable data classes
- `@Slf4j` for automatic logger injection
- `@RequiredArgsConstructor` for constructor injection

### Logback
- Console and file appenders
- DEBUG logging for testbed package
- Logs written to `target/test.log`

### Test Types
- **Unit tests** (Surefire): `*Test.java`
- **Integration tests** (Failsafe): `*IT.java`
- **Stack trace tests**: Produce various exceptions for parsing

## Usage

### Test with failing tests

```bash
cd test-project
mvn clean verify
```

This will:
1. Compile with Lombok annotation processing
2. Run unit tests (Surefire) - some will fail
3. Run integration tests (Failsafe) - some will fail
4. Generate `target/llm-summary.json`

### View LLM Summary

```bash
cat target/llm-summary.json
```

### View Test Logs

```bash
cat target/test.log
```

### View Surefire Reports

```bash
ls target/surefire-reports/
cat target/surefire-reports/*.txt
```

### View Failsafe Reports

```bash
ls target/failsafe-reports/
cat target/failsafe-reports/*.txt
```

## Plugin Configuration

The plugin is pre-configured in `pom.xml` to run during the `verify` phase.

### Disable Plugin

```bash
mvn clean verify -DllmCompactor.enabled=false
```

### Change Output Path

```bash
mvn clean verify -DllmCompactor.outputPath=build/llm-summary.json
```

### Enable Debug Logging

```bash
mvn clean verify -X | tee build.log
```

## Expected Failures

The test project intentionally contains failing tests:

| Test Class | Failure Type |
|------------|--------------|
| `OrderServiceTest` | AssertionError (wrong expected value) |
| `StackTraceTest` | NullPointerException, IllegalStateException, etc. |
| `PaymentIT` | AssertionError (wrong refund amount) |
| `OrderProcessorIT` | AssertionError (wrong total) |

## Stack Trace Test Cases

`StackTraceTest.java` produces various exception types:

- `NullPointerException` - null dereference
- `IllegalStateException` - business logic error
- Nested exceptions - wrapped exception chain
- `ArrayIndexOutOfBoundsException` - array access error
- `ArithmeticException` - division by zero

These test the `StackTraceCompressor` functionality.
