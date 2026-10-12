package com.ridehailing.pricing;

import com.ridehailing.domain.CarType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Computes a ride's fare. The order of steps is a business rule and is kept explicit here:
 * rate card (tiers + minimum fare) -> surge multiplier -> coupon discount -> floor at zero.
 *
 * <p>Each step's result is rounded to paise before the next step so the breakdown shown to the
 * rider always adds up.
 */
public class FareCalculator {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final Map<CarType, RateCard> rateCards;

    public FareCalculator(Map<CarType, ? extends RateCard> rateCards) {
        this.rateCards = new EnumMap<>(rateCards);
        for (CarType type : CarType.values()) {
            if (!this.rateCards.containsKey(type)) {
                throw new IllegalArgumentException("No rate card configured for " + type);
            }
        }
    }

    /**
     * @param billedCarType the car type the rider pays for (the requested type, even when upgraded)
     * @param discount      the coupon discount, or {@code null} when no coupon was applied
     */
    public FareBreakdown calculate(CarType billedCarType, BigDecimal distanceKm,
                                   BigDecimal surgeMultiplier, Discount discount) {
        Objects.requireNonNull(surgeMultiplier, "surgeMultiplier");
        if (surgeMultiplier.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("surgeMultiplier must be at least 1");
        }
        BigDecimal baseFare = money(rateCards.get(billedCarType).fareFor(distanceKm));
        BigDecimal surgedFare = money(baseFare.multiply(surgeMultiplier));
        BigDecimal discountAmount = discount == null
                ? money(BigDecimal.ZERO)
                : money(discount.amountOff(surgedFare).min(surgedFare));
        BigDecimal total = surgedFare.subtract(discountAmount);
        return new FareBreakdown(
                distanceKm.setScale(MONEY_SCALE, ROUNDING),
                baseFare, surgeMultiplier, surgedFare, discountAmount, total);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, ROUNDING);
    }
}
