package io.llmcompactor.testbed;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that produce stack traces for LLM Compactor to parse.
 */
class StackTraceTest {

    private final PaymentService paymentService = new PaymentService();

    @Test
    void testNullPointerException() {
        Order order = null;
        // This will produce a NullPointerException with stack trace
        String id = order.getId();
        assertEquals("ORD-001", id);
    }

    @Test
    void testIllegalStateException() {
        Order order = new Order("ORD-001", new BigDecimal("100.00"));
        
        // Simulate a business logic error that throws IllegalStateException
        throw new IllegalStateException("Payment gateway unavailable");
    }

    @Test
    void testNestedException() {
        try {
            processOrderWithNestedError();
        } catch (RuntimeException e) {
            // Re-wrap to create nested stack trace
            throw new RuntimeException("Order processing failed", e);
        }
    }

    private void processOrderWithNestedError() {
        validateOrder();
        paymentService.processPayment(new Order("ORD-002", new BigDecimal("50.00")));
    }

    private void validateOrder() {
        throw new IllegalArgumentException("Order validation failed");
    }

    @Test
    void testArrayIndexOutOfBounds() {
        String[] items = {"item1", "item2"};
        // This will produce ArrayIndexOutOfBoundsException
        String item = items[5];
        assertEquals("item3", item);
    }

    @Test
    void testArithmeticException() {
        int result = 10 / 0;
        assertEquals(5, result);
    }
}
