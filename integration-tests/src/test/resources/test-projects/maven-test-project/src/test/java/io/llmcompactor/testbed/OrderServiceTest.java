package io.llmcompactor.testbed;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.logging.log4j.LogManager;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {

    private static final Logger slf4jLogger = LoggerFactory.getLogger(OrderServiceTest.class);
    private static final org.apache.logging.log4j.Logger log4jLogger = LogManager.getLogger(OrderServiceTest.class);

    @Test
    void testOrderCreation() {
        slf4jLogger.info("SLF4J/Logback: Testing order creation");
        log4jLogger.info("Log4j2: Testing order creation");
        System.out.println("System.out: Creating order ORD-100");
        Order order = new Order("ORD-100", new BigDecimal("250.00"));
        slf4jLogger.debug("SLF4J/Logback: Order created with id: {}", order.getId());
        log4jLogger.debug("Log4j2: Order created with id: {}", order.getId());
        assertEquals("ORD-100", order.getId());
        assertEquals(new BigDecimal("250.00"), order.getAmount());
    }

    @Test
    void testOrderWithDiscount() {
        slf4jLogger.info("SLF4J/Logback: Testing discount calculation");
        log4jLogger.info("Log4j2: Testing discount calculation");
        System.out.println("System.out: Applying 10% discount to order ORD-101");
        Order order = new Order("ORD-101", new BigDecimal("100.00"));
        BigDecimal expected = new BigDecimal("90.00");
        BigDecimal actual = order.getAmount().multiply(new BigDecimal("0.9"));
        slf4jLogger.debug("SLF4J/Logback: Expected: {}, Actual: {}", expected, actual);
        log4jLogger.debug("Log4j2: Expected: {}, Actual: {}", expected, actual);

        // This assertion will fail intentionally
        assertEquals(expected, actual, "Discount calculation failed");
    }

    @Test
    void testRefundProcessing() {
        slf4jLogger.info("SLF4J/Logback: Testing refund processing");
        log4jLogger.info("Log4j2: Testing refund processing");
        System.err.println("System.err: Processing refund for order ORD-102");
        PaymentService service = new PaymentService();
        Order order = new Order("ORD-102", new BigDecimal("50.00"));

        Refund refund = service.processRefund(order);

        assertNotNull(refund);
        assertEquals("ORD-102", refund.getOrderId());
        assertEquals(new BigDecimal("50.00"), refund.getAmount());
        slf4jLogger.info("SLF4J/Logback: Refund processed successfully");
        log4jLogger.info("Log4j2: Refund processed successfully");
    }

    @Test
    void testNullOrderId() {
        slf4jLogger.info("SLF4J/Logback: Testing null order ID handling");
        log4jLogger.info("Log4j2: Testing null order ID handling");
        Order order = new Order(null, new BigDecimal("30.00"));
        // This test expects null to be allowed
        assertNull(order.getId());
        slf4jLogger.debug("SLF4J/Logback: Null order ID accepted");
        log4jLogger.debug("Log4j2: Null order ID accepted");
    }

    @Test
    void testLargeOrder() {
        slf4jLogger.info("SLF4J/Logback: Testing large order");
        log4jLogger.info("Log4j2: Testing large order");
        System.out.println("System.out: Creating large order ORD-LARGE");
        Order order = new Order("ORD-LARGE", new BigDecimal("999999.99"));
        assertTrue(order.getAmount().compareTo(new BigDecimal("100000")) > 0);
        slf4jLogger.info("SLF4J/Logback: Large order validated");
        log4jLogger.info("Log4j2: Large order validated");
    }
}
