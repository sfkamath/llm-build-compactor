package io.llmcompactor.testbed;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceTest.class);

    // ===== SLF4J/Logback Tests =====
    
    @Test
    void testSlf4jInfoLog() {
        log.info("SLF4J INFO: Testing order creation");
        Order order = new Order("ORD-SLF4J-100", new BigDecimal("250.00"));
        assertEquals("ORD-SLF4J-100", order.getId());
    }

    @Test
    void testSlf4jDebugLog() {
        log.debug("SLF4J DEBUG: Testing discount calculation");
        Order order = new Order("ORD-SLF4J-101", new BigDecimal("100.00"));
        BigDecimal actual = order.getAmount().multiply(new BigDecimal("0.9"));
        log.debug("SLF4J DEBUG: Expected: 90.00, Actual: {}", actual);
        assertEquals(new BigDecimal("90.00"), actual);
    }

    // ===== System.out/System.err Tests =====
    
    @Test
    void testSystemOut() {
        System.out.println("System.out: Creating order ORD-SYS-100");
        Order order = new Order("ORD-SYS-100", new BigDecimal("250.00"));
        System.out.println("System.out: Order created with id: " + order.getId());
        assertEquals("ORD-SYS-100", order.getId());
    }

    @Test
    void testSystemErr() {
        System.err.println("System.err: Processing refund for order ORD-SYS-102");
        PaymentService service = new PaymentService();
        Order order = new Order("ORD-SYS-102", new BigDecimal("50.00"));
        Refund refund = service.processRefund(order);
        System.err.println("System.err: Refund processed: " + refund.getAmount());
        assertNotNull(refund);
        assertEquals("ORD-SYS-102", refund.getOrderId());
    }

    // ===== Mixed Logging Tests (SLF4J + System.out/System.err) =====
    
    @Test
    void testMixedLogging() {
        log.info("SLF4J: Starting mixed logging test");
        System.out.println("System.out: Creating order ORD-MIXED-100");
        
        Order order = new Order("ORD-MIXED-100", new BigDecimal("250.00"));
        
        log.debug("SLF4J: Order created: {}", order.getId());
        System.out.println("System.out: Order amount: " + order.getAmount());
        
        assertEquals("ORD-MIXED-100", order.getId());
    }

    // ===== Failing Tests (to verify logs are captured on failure) =====
    
    @Test
    void testSlf4jFailing() {
        log.info("SLF4J: This test will fail");
        log.debug("SLF4J: Debug message before failure");
        fail("SLF4J: Intentional failure");
    }

    @Test
    void testSystemOutFailing() {
        System.out.println("System.out: This test will fail");
        System.out.println("System.out: Expected failure message");
        fail("System.out: Intentional failure");
    }

    @Test
    void testMixedLoggingFailing() {
        log.info("SLF4J: Mixed logging test will fail");
        System.out.println("System.out: Mixed logging test will fail");
        
        log.debug("SLF4J: Debug message before failure");
        System.err.println("System.err: Error message before failure");
        
        fail("Mixed: Intentional failure");
    }
}
