package com.ridehailing.pricing;

import com.ridehailing.domain.CarType;
import com.ridehailing.pricing.TieredRateCard.Slab;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Rate cards matching application.yml, so tests and the running app agree on prices. */
public final class PricingFixtures {

    public static final TieredRateCard HATCHBACK = new TieredRateCard(new BigDecimal("50"), List.of(
            upTo("2", "10"), upTo("5", "8"), beyond("5")));

    public static final TieredRateCard SEDAN = new TieredRateCard(new BigDecimal("80"), List.of(
            upTo("2", "15"), upTo("5", "12"), beyond("8")));

    public static Slab upTo(String km, String ratePerKm) {
        return new Slab(new BigDecimal(km), new BigDecimal(ratePerKm));
    }

    public static Slab beyond(String ratePerKm) {
        return new Slab(null, new BigDecimal(ratePerKm));
    }

    public static FareCalculator fareCalculator() {
        return new FareCalculator(Map.of(CarType.HATCHBACK, HATCHBACK, CarType.SEDAN, SEDAN));
    }

    private PricingFixtures() {
    }
}
