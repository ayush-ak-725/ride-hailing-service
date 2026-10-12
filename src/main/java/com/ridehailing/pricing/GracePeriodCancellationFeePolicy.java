package com.ridehailing.pricing;

import com.ridehailing.domain.CarType;
import com.ridehailing.domain.Ride;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * Free cancellation up to and including {@code gracePeriod} after booking; afterwards a flat fee
 * for the billed car type (so an upgraded Hatchback rider pays the Hatchback fee).
 */
public class GracePeriodCancellationFeePolicy implements CancellationFeePolicy {

    private final Duration gracePeriod;
    private final Map<CarType, BigDecimal> feeByCarType;

    public GracePeriodCancellationFeePolicy(Duration gracePeriod, Map<CarType, BigDecimal> feeByCarType) {
        if (gracePeriod.isNegative()) {
            throw new IllegalArgumentException("gracePeriod must not be negative");
        }
        this.gracePeriod = gracePeriod;
        this.feeByCarType = new EnumMap<>(feeByCarType);
        for (CarType type : CarType.values()) {
            BigDecimal fee = this.feeByCarType.get(type);
            if (fee == null || fee.signum() < 0) {
                throw new IllegalArgumentException("A non-negative cancellation fee is required for " + type);
            }
        }
    }

    @Override
    public BigDecimal feeFor(Ride ride, Instant cancelledAt) {
        boolean withinGrace = !cancelledAt.isAfter(ride.startedAt().plus(gracePeriod));
        BigDecimal fee = withinGrace ? BigDecimal.ZERO : feeByCarType.get(ride.billedCarType());
        return fee.setScale(2, RoundingMode.HALF_UP);
    }
}
