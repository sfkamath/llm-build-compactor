package io.llmcompactor.testbed;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for order processing.
 */
class OrderProcessorIT {

    @Test
    void it_shouldProcessOrderWithValidData() {
        PaymentService paymentService = new PaymentService();
        OrderProcessor processor = new OrderProcessor(paymentService);
        
        Order order = new Order("PROC-001", new BigDecimal("99.99"));
        
        assertDoesNotThrow(() -> processor.processOrder(order));
    }

    @Test
    void it_shouldRejectNullOrder() {
        PaymentService paymentService = new PaymentService();
        OrderProcessor processor = new OrderProcessor(paymentService);
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> processor.processOrder(null)
        );
        assertEquals("Order cannot be null", exception.getMessage());
    }

    @Test
    void it_shouldCalculateTotalCorrectly() {
        PaymentService paymentService = new PaymentService();
        OrderProcessor processor = new OrderProcessor(paymentService);
        
        Order[] orders = {
            new Order("PROC-002", new BigDecimal("100.00")),
            new Order("PROC-003", new BigDecimal("50.00")),
            new Order("PROC-004", new BigDecimal("25.00"))
        };
        
        BigDecimal total = processor.calculateTotal(orders);
        
        // Intentional failure - wrong expected total
        assertEquals(new BigDecimal("100.00"), total, 
            "Total should be sum of all orders");
    }

    @Test
    void it_shouldHandleEmptyOrderList() {
        PaymentService paymentService = new PaymentService();
        OrderProcessor processor = new OrderProcessor(paymentService);
        
        Order[] orders = {};
        BigDecimal total = processor.calculateTotal(orders);
        
        assertEquals(BigDecimal.ZERO, total);
    }

    @Test
    void it_shouldProcessRefundThroughProcessor() {
        PaymentService paymentService = new PaymentService();
        OrderProcessor processor = new OrderProcessor(paymentService);
        
        Order order = new Order("PROC-005", new BigDecimal("150.00"));
        
        Refund refund = processor.refundOrder(order);
        
        assertNotNull(refund);
        assertEquals("PROC-005", refund.getOrderId());
        assertEquals(new BigDecimal("150.00"), refund.getAmount());
    }
}
