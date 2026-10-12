package com.ridehailing.pricing;

import java.math.BigDecimal;

/** Distance-based pricing for one car type, before surge and discounts. */
public interface RateCard {

    /** Fare for a ride of the given distance, including any minimum-fare floor. */
    BigDecimal fareFor(BigDecimal distanceKm);
}
