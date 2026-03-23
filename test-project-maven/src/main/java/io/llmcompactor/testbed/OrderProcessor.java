package io.llmcompactor.testbed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;

public class OrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessor.class);

    private final PaymentService paymentService;

    public OrderProcessor(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

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
