package com.ridehailing.service;

import com.ridehailing.config.RideProperties;
import com.ridehailing.config.RideProperties.MatchingStrategyType;
import com.ridehailing.domain.CarType;
import com.ridehailing.domain.Driver;
import com.ridehailing.domain.Location;
import com.ridehailing.domain.User;
import com.ridehailing.matching.DriverAllocator;
import com.ridehailing.matching.NearestDriverStrategy;
import com.ridehailing.pricing.GracePeriodCancellationFeePolicy;
import com.ridehailing.pricing.NoSurge;
import com.ridehailing.pricing.PricingFixtures;
import com.ridehailing.pricing.SurgePricing;
import com.ridehailing.repository.CouponRepository;
import com.ridehailing.repository.DriverRepository;
import com.ridehailing.repository.RideRepository;
import com.ridehailing.repository.UserRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** The real object graph wired by hand: no Spring, no mocks, 5 km radius, 2 min free cancellation, no surge by default. */
final class TestApp {

    static final double RADIUS_KM = 5;

    static final Duration GRACE_PERIOD = Duration.ofMinutes(2);

    final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    final UserService users = new UserService(new UserRepository());
    final DriverService drivers;
    final CouponService coupons = new CouponService(new CouponRepository());
    final RideService rides;

    private final AtomicInteger counter = new AtomicInteger();

    TestApp() {
        this(drivers -> new NoSurge());
    }

    /** @param surge builds the surge strategy from the driver repository this app uses */
    TestApp(Function<DriverRepository, SurgePricing> surge) {
        DriverRepository driverRepository = new DriverRepository();
        drivers = new DriverService(driverRepository);
        DriverAllocator allocator = new DriverAllocator(driverRepository, new NearestDriverStrategy(),
                new RideProperties(RADIUS_KM, MatchingStrategyType.NEAREST));
        rides = new RideService(users, drivers, coupons, allocator, surge.apply(driverRepository),
                PricingFixtures.fareCalculator(),
                new GracePeriodCancellationFeePolicy(GRACE_PERIOD,
                        Map.of(CarType.HATCHBACK, new BigDecimal("25"), CarType.SEDAN, new BigDecimal("40"))),
                new RideRepository(), clock);
    }

    User user() {
        int n = counter.incrementAndGet();
        return users.register("Rider " + n, "90000" + String.format("%05d", n));
    }

    Driver driver(CarType carType, double x, double y) {
        int n = counter.incrementAndGet();
        return drivers.register("Driver " + n, "80000" + String.format("%05d", n), "KA01-" + n,
                carType, new Location(x, y), 4.5);
    }
}
