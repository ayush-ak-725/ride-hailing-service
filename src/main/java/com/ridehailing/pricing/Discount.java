package com.ridehailing.pricing;

import java.math.BigDecimal;

/** A coupon's discount rule. New coupon kinds (e.g. flat amount) are new implementations. */
public interface Discount {

    /** Discount amount for the given fare. May exceed the fare; callers cap it. */
    BigDecimal amountOff(BigDecimal fare);
}
