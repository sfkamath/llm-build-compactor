package io.llmcompactor.testbed;

import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;

@Slf4j
public class PaymentService {

    public boolean processPayment(Order order) {
        log.info("Processing payment for order: {}", order != null ? order.getId() : "null");
        
        if (order == null) {
            log.error("Order cannot be null");
            throw new IllegalArgumentException("Order cannot be null");
        }
        
        if (order.getAmount() == null || order.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Invalid order amount: {}", order.getAmount());
            throw new IllegalArgumentException("Order amount must be positive");
        }
        
        log.info("Payment processed successfully for order: {}", order.getId());
        return true;
    }

    public Refund processRefund(Order order) {
        log.info("Processing refund for order: {}", order != null ? order.getId() : "null");
        
        if (order == null) {
            log.error("Order cannot be null for refund");
            throw new IllegalArgumentException("Order cannot be null");
        }
        
        Refund refund = new Refund(order.getId(), order.getAmount());
        log.info("Refund processed: {}", refund);
        return refund;
    }
}
