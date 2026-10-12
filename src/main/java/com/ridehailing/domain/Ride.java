package com.ridehailing.domain;

import com.ridehailing.error.DomainException;
import com.ridehailing.error.ErrorCode;
import com.ridehailing.pricing.Discount;
import com.ridehailing.pricing.FareBreakdown;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A ride from booking to completion or cancellation. Everything needed to price the ride
 * (billed car type, surge, coupon discount) is captured at booking, so later changes such as
 * deleting the coupon do not change what the rider was promised.
 */
public class Ride {

    private final String id;
    private final String userId;
    private final String driverId;
    private final CarType requestedCarType;
    private final CarType assignedCarType;
    private final Location pickup;
    private final BigDecimal surgeMultiplier;
    private final String couponCode;
    private final Discount discount;
    private final Instant startedAt;

    private RideStatus status = RideStatus.ONGOING;
    private Location dropoff;
    private FareBreakdown fare;
    private BigDecimal cancellationFee;
    private Instant endedAt;

    public Ride(String id, String userId, String driverId, CarType requestedCarType, CarType assignedCarType,
                Location pickup, BigDecimal surgeMultiplier, String couponCode, Discount discount,
                Instant startedAt) {
        this.id = id;
        this.userId = userId;
        this.driverId = driverId;
        this.requestedCarType = requestedCarType;
        this.assignedCarType = assignedCarType;
        this.pickup = pickup;
        this.surgeMultiplier = surgeMultiplier;
        this.couponCode = couponCode;
        this.discount = discount;
        this.startedAt = startedAt;
    }

    public synchronized void complete(Location dropoff, FareBreakdown fare, Instant endedAt) {
        requireOngoing();
        this.status = RideStatus.COMPLETED;
        this.dropoff = dropoff;
        this.fare = fare;
        this.endedAt = endedAt;
    }

    public synchronized void cancel(BigDecimal cancellationFee, Instant endedAt) {
        requireOngoing();
        this.status = RideStatus.CANCELLED;
        this.cancellationFee = cancellationFee;
        this.endedAt = endedAt;
    }

    private void requireOngoing() {
        if (status != RideStatus.ONGOING) {
            throw new DomainException(ErrorCode.RIDE_NOT_ONGOING, "Ride " + id + " is already " + status);
        }
    }

    /** Riders pay for what they asked for; a free upgrade does not change the price. */
    public CarType billedCarType() {
        return requestedCarType;
    }

    public boolean upgraded() {
        return requestedCarType != assignedCarType;
    }

    public String id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public String driverId() {
        return driverId;
    }

    public CarType requestedCarType() {
        return requestedCarType;
    }

    public CarType assignedCarType() {
        return assignedCarType;
    }

    public Location pickup() {
        return pickup;
    }

    public BigDecimal surgeMultiplier() {
        return surgeMultiplier;
    }

    public String couponCode() {
        return couponCode;
    }

    public Discount discount() {
        return discount;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public synchronized RideStatus status() {
        return status;
    }

    public synchronized Location dropoff() {
        return dropoff;
    }

    public synchronized FareBreakdown fare() {
        return fare;
    }

    public synchronized BigDecimal cancellationFee() {
        return cancellationFee;
    }

    public synchronized Instant endedAt() {
        return endedAt;
    }
}
