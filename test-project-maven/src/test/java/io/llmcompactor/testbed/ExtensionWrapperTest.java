package io.llmcompactor.testbed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to reproduce stack trace parsing issue where wrong file is picked
 * (last project frame instead of first).
 */
class ExtensionWrapperTest {

    @Test
    void testWithHelperInDifferentFile() {
        // Call helper in different file - this is where the failure happens
        TestHelper.process("test");
    }
}
