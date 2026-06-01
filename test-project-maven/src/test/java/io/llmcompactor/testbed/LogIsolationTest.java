package io.llmcompactor.testbed;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

/**
 * Verifies that showFailedTestLogs captures only the failing test's output.
 * The markers must survive cleanTestLogLine (plain System.out, no timestamps or log levels).
 */
class LogIsolationTest {

    @Test
    void testPassingWithOutput() {
        System.out.println("LOG_ISOLATION_PASSING_ONLY");
    }

    @Test
    void testFailingWithOutput() {
        System.out.println("LOG_ISOLATION_FAILING_ONLY");
        fail("intentional failure for log isolation test");
    }
}
