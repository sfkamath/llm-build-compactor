package io.llmcompactor.testbed;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for payment processing.
 * These tests are designed to be run with Maven Failsafe Plugin.
 */
class PaymentIT {

    private final PaymentService paymentService = new PaymentService();

    @Test
    void it_shouldProcessValidPayment() {
        Order order = new Order("IT-001", new BigDecimal("150.00"));
        
        boolean result = paymentService.processPayment(order);
        
        assertTrue(result, "Payment should be processed successfully");
    }

    @Test
    void it_shouldRejectNullOrder() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> paymentService.processPayment(null)
        );
        assertEquals("Order cannot be null", exception.getMessage());
    }

    @Test
    void it_shouldProcessRefundAfterPayment() {
        Order order = new Order("IT-002", new BigDecimal("75.50"));
        
        paymentService.processPayment(order);
        Refund refund = paymentService.processRefund(order);
        
        assertNotNull(refund);
        assertEquals("IT-002", refund.getOrderId());
        assertEquals(new BigDecimal("75.50"), refund.getAmount());
    }

    @Test
    void it_shouldCalculateCorrectRefundAmount() {
        Order order = new Order("IT-003", new BigDecimal("200.00"));
        
        Refund refund = paymentService.processRefund(order);
        
        // Intentional failure - wrong expected amount
        assertEquals(new BigDecimal("100.00"), refund.getAmount(), 
            "Refund amount should match order amount");
    }

    @Test
    void it_shouldHandleMultipleOrders() {
        Order[] orders = {
            new Order("IT-004", new BigDecimal("50.00")),
            new Order("IT-005", new BigDecimal("100.00")),
            new Order("IT-006", new BigDecimal("25.00"))
        };
        
        for (Order order : orders) {
            boolean result = paymentService.processPayment(order);
            assertTrue(result);
        }
    }
}
