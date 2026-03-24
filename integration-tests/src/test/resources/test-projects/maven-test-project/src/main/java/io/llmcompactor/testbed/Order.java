package io.llmcompactor.testbed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;

public class Order {

    private static final Logger log = LoggerFactory.getLogger(Order.class);

    private String id;
    private BigDecimal amount;

    public Order(String id, BigDecimal amount) {
        log.debug("Creating order {} with amount {}", id, amount);
        this.id = id;
        this.amount = amount;
    }

    public String getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return "Order{id='" + id + "', amount=" + amount + "}";
    }
}
