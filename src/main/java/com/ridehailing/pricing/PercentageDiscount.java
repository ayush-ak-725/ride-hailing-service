package com.ridehailing.pricing;

import java.math.BigDecimal;
import java.util.Objects;

/** {@code percent}% off the fare, optionally capped at {@code maxAmount}. */
public record PercentageDiscount(BigDecimal percent, BigDecimal maxAmount) implements Discount {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public PercentageDiscount {
        Objects.requireNonNull(percent, "percent");
        if (percent.signum() <= 0 || percent.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("percent must be in (0, 100]");
        }
        if (maxAmount != null && maxAmount.signum() <= 0) {
            throw new IllegalArgumentException("maxAmount must be positive when set");
        }
    }

    @Override
    public BigDecimal amountOff(BigDecimal fare) {
        BigDecimal amount = fare.multiply(percent).divide(HUNDRED);
        return maxAmount == null ? amount : amount.min(maxAmount);
    }
}
