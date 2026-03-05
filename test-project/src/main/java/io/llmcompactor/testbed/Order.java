package io.llmcompactor.testbed;

import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;

@Slf4j
public class Order {

    private final String id;
    private final BigDecimal amount;

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
