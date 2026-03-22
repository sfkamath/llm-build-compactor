package io.llmcompactor.testbed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;

@Slf4j
@RequiredArgsConstructor
public class OrderProcessor {

    private final PaymentService paymentService;

    public void processOrder(Order order) {
        log.info("Processing order: {}", order != null ? order.getId() : "null");
        
        if (order == null) {
            log.error("Order cannot be null");
            throw new IllegalArgumentException("Order cannot be null");
        }
        
        paymentService.processPayment(order);
        log.info("Order processed successfully: {}", order.getId());
    }

    public Refund refundOrder(Order order) {
        log.info("Refunding order: {}", order != null ? order.getId() : "null");
        return paymentService.processRefund(order);
    }

    public BigDecimal calculateTotal(Order[] orders) {
        log.debug("Calculating total for {} orders", orders != null ? orders.length : 0);
        
        BigDecimal total = BigDecimal.ZERO;
        for (Order order : orders) {
            total = total.add(order.getAmount());
        }
        
        log.info("Total calculated: {}", total);
        return total;
    }
}
