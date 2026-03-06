package io.llmcompactor.testbed;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;

@Slf4j
@Value
public class Refund {
    String orderId;
    BigDecimal amount;

    public Refund(String orderId, BigDecimal amount) {
        log.debug("Creating refund for order {} with amount {}", orderId, amount);
        this.orderId = orderId;
        this.amount = amount;
    }
}
