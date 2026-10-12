package com.ridehailing.api.dto;

import com.ridehailing.domain.CarType;
import com.ridehailing.domain.Ride;
import com.ridehailing.domain.RideStatus;
import com.ridehailing.pricing.FareBreakdown;

import java.math.BigDecimal;
import java.time.Instant;

/** {@code fare} is set once the ride is completed; {@code cancellationFee} once it is cancelled. */
public record RideResponse(
        String id,
        String userId,
        String driverId,
        RideStatus status,
        CarType requestedCarType,
        CarType assignedCarType,
        boolean upgraded,
        LocationDto pickup,
        LocationDto dropoff,
        String couponCode,
        BigDecimal surgeMultiplier,
        Instant startedAt,
        Instant endedAt,
        FareBreakdown fare,
        BigDecimal cancellationFee) {

    public static RideResponse from(Ride ride) {
        return new RideResponse(ride.id(), ride.userId(), ride.driverId(), ride.status(),
                ride.requestedCarType(), ride.assignedCarType(), ride.upgraded(),
                LocationDto.from(ride.pickup()), LocationDto.from(ride.dropoff()), ride.couponCode(),
                ride.surgeMultiplier(), ride.startedAt(), ride.endedAt(), ride.fare(), ride.cancellationFee());
    }
}
