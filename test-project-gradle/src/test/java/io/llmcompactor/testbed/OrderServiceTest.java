package io.llmcompactor.testbed;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {

    @Test
    void testOrderCreation() {
        Order order = new Order("ORD-100", new BigDecimal("250.00"));
        assertEquals("ORD-100", order.getId());
        assertEquals(new BigDecimal("250.00"), order.getAmount());
    }

    @Test
    void testOrderWithDiscount() {
        Order order = new Order("ORD-101", new BigDecimal("100.00"));
        BigDecimal expected = new BigDecimal("90.00");
        BigDecimal actual = order.getAmount().multiply(new BigDecimal("0.9"));
        
        // This assertion will fail intentionally
        assertEquals(expected, actual, "Discount calculation failed");
    }

    @Test
    void testRefundProcessing() {
        PaymentService service = new PaymentService();
        Order order = new Order("ORD-102", new BigDecimal("50.00"));
        
        Refund refund = service.processRefund(order);
        
        assertNotNull(refund);
        assertEquals("ORD-102", refund.getOrderId());
        assertEquals(new BigDecimal("50.00"), refund.getAmount());
    }

    @Test
    void testNullOrderId() {
        Order order = new Order(null, new BigDecimal("30.00"));
        // This test expects null to be allowed
        assertNull(order.getId());
    }

    @Test
    void testLargeOrder() {
        Order order = new Order("ORD-LARGE", new BigDecimal("999999.99"));
        assertTrue(order.getAmount().compareTo(new BigDecimal("100000")) > 0);
    }
}
