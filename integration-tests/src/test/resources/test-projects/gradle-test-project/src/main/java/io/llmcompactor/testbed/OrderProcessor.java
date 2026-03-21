package io.llmcompactor.testbed;

import java.math.BigDecimal;

public class OrderProcessor {

    private final PaymentService paymentService;

    public OrderProcessor(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public void processOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        paymentService.processPayment(order);
    }

    public Refund refundOrder(Order order) {
        return paymentService.processRefund(order);
    }

    public BigDecimal calculateTotal(Order[] orders) {
        if (orders == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal total = BigDecimal.ZERO;
        for (Order order : orders) {
            total = total.add(order.getAmount());
        }
        return total;
    }
}
