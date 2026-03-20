package io.llmcompactor.testbed;

import java.math.BigDecimal;

public class Refund {
    private final String orderId;
    private final BigDecimal amount;

    public Refund(String orderId, BigDecimal amount) {
        this.orderId = orderId;
        this.amount = amount;
    }

    public String getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return "Refund{orderId='" + orderId + "', amount=" + amount + "}";
    }
}
