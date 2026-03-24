package io.llmcompactor.testbed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;

public class Refund {

    private static final Logger log = LoggerFactory.getLogger(Refund.class);

    private final String orderId;
    private final BigDecimal amount;

    public Refund(String orderId, BigDecimal amount) {
        log.debug("Creating refund for order {} with amount {}", orderId, amount);
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
