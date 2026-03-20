package io.llmcompactor.testbed;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceTest.class);

    @Test
    void testOrderCreation() {
        log.info("Testing order creation");
        System.out.println("Creating order ORD-100");
        Order order = new Order("ORD-100", new BigDecimal("250.00"));
        log.debug("Order created with id: {}", order.getId());
        assertEquals("ORD-100", order.getId());
        assertEquals(new BigDecimal("250.00"), order.getAmount());
    }

    @Test
    void testOrderWithDiscount() {
        log.info("Testing discount calculation");
        System.out.println("Applying 10% discount to order ORD-101");
        Order order = new Order("ORD-101", new BigDecimal("100.00"));
        BigDecimal expected = new BigDecimal("90.00");
        BigDecimal actual = order.getAmount().multiply(new BigDecimal("0.9"));
        log.debug("Expected: {}, Actual: {}", expected, actual);

        // This assertion will fail intentionally
        assertEquals(expected, actual, "Discount calculation failed");
    }

    @Test
    void testRefundProcessing() {
        log.info("Testing refund processing");
        System.err.println("Processing refund for order ORD-102");
        PaymentService service = new PaymentService();
        Order order = new Order("ORD-102", new BigDecimal("50.00"));

        Refund refund = service.processRefund(order);

        assertNotNull(refund);
        assertEquals("ORD-102", refund.getOrderId());
        assertEquals(new BigDecimal("50.00"), refund.getAmount());
        log.info("Refund processed successfully");
    }

    @Test
    void testNullOrderId() {
        log.info("Testing null order ID handling");
        Order order = new Order(null, new BigDecimal("30.00"));
        // This test expects null to be allowed
        assertNull(order.getId());
        log.debug("Null order ID accepted");
    }

    @Test
    void testLargeOrder() {
        log.info("Testing large order");
        System.out.println("Creating large order ORD-LARGE");
        Order order = new Order("ORD-LARGE", new BigDecimal("999999.99"));
        assertTrue(order.getAmount().compareTo(new BigDecimal("100000")) > 0);
        log.info("Large order validated");
    }
}
