package io.llmcompactor.testbed;

/**
 * Separate helper class in different file to reproduce multi-file stack trace issue
 */
public class TestHelper {
    public static String process(String input) {
        // Simulates logic in a different file
        throw new AssertionError("Expected 'expected' but was: 'wrong'");
    }
}
