package io.llmcompactor.testbed;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentServiceTest {

    private final PaymentService paymentService = new PaymentService();

    @Test
    void testProcessPayment_validOrder() {
        Order order = new Order("ORD-001", new BigDecimal("100.00"));
        boolean result = paymentService.processPayment(order);
        assertTrue(result);
    }

    @Test
    void testProcessPayment_nullOrder() {
        assertThrows(IllegalArgumentException.class, () -> {
            paymentService.processPayment(null);
        });
    }

    @Test
    void testProcessPayment_negativeAmount() {
        Order order = new Order("ORD-002", new BigDecimal("-50.00"));
        assertThrows(IllegalArgumentException.class, () -> {
            paymentService.processPayment(order);
        });
    }

    @Test
    void testProcessRefund_validOrder() {
        Order order = new Order("ORD-003", new BigDecimal("75.50"));
        Refund refund = paymentService.processRefund(order);
        assertNotNull(refund);
        assertEquals("ORD-003", refund.getOrderId());
        assertEquals(new BigDecimal("75.50"), refund.getAmount());
    }

    @Test
    void testProcessPayment_zeroAmount() {
        Order order = new Order("ORD-004", BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> {
            paymentService.processPayment(order);
        });
    }
}
