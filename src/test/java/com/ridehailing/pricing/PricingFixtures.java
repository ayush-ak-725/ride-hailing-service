package com.ridehailing.pricing;

import com.ridehailing.domain.CarType;
import com.ridehailing.pricing.TieredRateCard.Slab;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Rate cards matching application.yml, so tests and the running app agree on prices. */
public final class PricingFixtures {

    public static final TieredRateCard HATCHBACK = new TieredRateCard(new BigDecimal("50"), List.of(
            Slab.upTo("2", "10"), Slab.upTo("5", "8"), Slab.beyond("5")));

    public static final TieredRateCard SEDAN = new TieredRateCard(new BigDecimal("80"), List.of(
            Slab.upTo("2", "15"), Slab.upTo("5", "12"), Slab.beyond("8")));

    public static FareCalculator fareCalculator() {
        return new FareCalculator(Map.of(CarType.HATCHBACK, HATCHBACK, CarType.SEDAN, SEDAN));
    }

    private PricingFixtures() {
    }
}
