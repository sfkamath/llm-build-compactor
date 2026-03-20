package io.llmcompactor.testbed;

import java.math.BigDecimal;

public class PaymentService {

    public boolean processPayment(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        if (order.getAmount() == null || order.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Order amount must be positive");
        }

        return true;
    }

    public Refund processRefund(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        return new Refund(order.getId(), order.getAmount());
    }
}
