package com.ridehailing.service;

import com.ridehailing.domain.CarType;
import com.ridehailing.domain.Coupon;
import com.ridehailing.domain.Driver;
import com.ridehailing.domain.Location;
import com.ridehailing.domain.Ride;
import com.ridehailing.domain.RideStatus;
import com.ridehailing.domain.User;
import com.ridehailing.error.DomainException;
import com.ridehailing.error.ErrorCode;
import com.ridehailing.matching.DriverAllocator;
import com.ridehailing.pricing.CancellationFeePolicy;
import com.ridehailing.pricing.FareBreakdown;
import com.ridehailing.pricing.FareCalculator;
import com.ridehailing.pricing.SurgePricing;
import com.ridehailing.repository.RideRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class RideService {

    private final UserService userService;
    private final DriverService driverService;
    private final CouponService couponService;
    private final DriverAllocator driverAllocator;
    private final SurgePricing surgePricing;
    private final FareCalculator fareCalculator;
    private final CancellationFeePolicy cancellationFeePolicy;
    private final RideRepository rides;
    private final Clock clock;

    public RideService(UserService userService, DriverService driverService, CouponService couponService,
                       DriverAllocator driverAllocator, SurgePricing surgePricing, FareCalculator fareCalculator,
                       CancellationFeePolicy cancellationFeePolicy, RideRepository rides, Clock clock) {
        this.userService = userService;
        this.driverService = driverService;
        this.couponService = couponService;
        this.driverAllocator = driverAllocator;
        this.surgePricing = surgePricing;
        this.fareCalculator = fareCalculator;
        this.cancellationFeePolicy = cancellationFeePolicy;
        this.rides = rides;
        this.clock = clock;
    }

    /**
     * Books and starts a ride. Validation that cannot have side effects (user, coupon) happens
     * first; then the user and a driver are reserved atomically, and the user's reservation is
     * rolled back if no driver can be found.
     *
     * @param couponCode optional; an unknown or deleted code fails the booking
     */
    public Ride book(String userId, Location pickup, CarType carType, String couponCode) {
        User user = userService.get(userId);
        Coupon coupon = couponCode == null ? null : couponService.resolveForBooking(couponCode);
        BigDecimal surgeMultiplier = surgePricing.multiplierAt(pickup);

        if (!user.tryStartRide()) {
            throw new DomainException(ErrorCode.USER_HAS_ONGOING_RIDE, "User " + userId + " already has an ongoing ride");
        }
        Driver driver = driverAllocator.claim(carType, pickup).orElseThrow(() -> {
            user.finishRide();
            return new DomainException(ErrorCode.NO_DRIVER_AVAILABLE, "No driver available near the pickup point for " + carType);
        });

        Ride ride = new Ride(rides.nextId(), user.id(), driver.id(), carType, driver.cab().carType(), pickup,
                surgeMultiplier, coupon == null ? null : coupon.code(), coupon == null ? null : coupon.discount(),
                clock.instant());
        rides.save(ride);
        return ride;
    }

    /**
     * Ends the ride at the cab's current location and prices it. Distance is straight-line from
     * pickup to that location.
     */
    public Ride end(String rideId) {
        Ride ride = get(rideId);
        Driver driver = driverService.get(ride.driverId());
        Location dropoff = driver.cab().location();
        BigDecimal distanceKm = BigDecimal.valueOf(ride.pickup().distanceTo(dropoff));
        FareBreakdown fare = fareCalculator.calculate(
                ride.billedCarType(), distanceKm, ride.surgeMultiplier(), ride.discount());

        ride.complete(dropoff, fare, clock.instant());   // throws if the ride already ended
        driver.release();
        userService.get(ride.userId()).finishRide();
        return ride;
    }

    /** Cancels an ongoing ride, charging the fee set by the cancellation policy. */
    public Ride cancel(String rideId) {
        Ride ride = get(rideId);
        Instant now = clock.instant();
        ride.cancel(cancellationFeePolicy.feeFor(ride, now), now);   // throws if the ride already ended
        driverService.get(ride.driverId()).release();
        userService.get(ride.userId()).finishRide();
        return ride;
    }

    public Ride get(String rideId) {
        return rides.findById(rideId)
                .orElseThrow(() -> new DomainException(ErrorCode.RIDE_NOT_FOUND, "Ride " + rideId + " not found"));
    }

    public RideHistory historyForUser(String userId) {
        userService.get(userId);
        return toHistory(rides.findByUserId(userId));
    }

    public RideHistory historyForDriver(String driverId) {
        driverService.get(driverId);
        return toHistory(rides.findByDriverId(driverId));
    }

    private static RideHistory toHistory(List<Ride> rides) {
        return new RideHistory(
                withStatus(rides, RideStatus.ONGOING),
                withStatus(rides, RideStatus.COMPLETED),
                withStatus(rides, RideStatus.CANCELLED));
    }

    private static List<Ride> withStatus(List<Ride> rides, RideStatus status) {
        return rides.stream().filter(ride -> ride.status() == status).toList();
    }
}
