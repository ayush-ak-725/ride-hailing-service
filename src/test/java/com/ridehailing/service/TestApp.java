package com.ridehailing.service;

import com.ridehailing.config.RideProperties;
import com.ridehailing.config.RideProperties.MatchingStrategyType;
import com.ridehailing.domain.CarType;
import com.ridehailing.domain.Driver;
import com.ridehailing.domain.Location;
import com.ridehailing.domain.User;
import com.ridehailing.matching.DriverAllocator;
import com.ridehailing.matching.NearestDriverStrategy;
import com.ridehailing.pricing.NoSurge;
import com.ridehailing.pricing.PricingFixtures;
import com.ridehailing.repository.CouponRepository;
import com.ridehailing.repository.DriverRepository;
import com.ridehailing.repository.RideRepository;
import com.ridehailing.repository.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

/** The real object graph wired by hand: no Spring, no mocks, 5 km search radius. */
final class TestApp {

    static final double RADIUS_KM = 5;

    final Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
    final UserService users = new UserService(new UserRepository());
    final DriverService drivers;
    final CouponService coupons = new CouponService(new CouponRepository());
    final RideService rides;

    private final AtomicInteger counter = new AtomicInteger();

    TestApp() {
        DriverRepository driverRepository = new DriverRepository();
        drivers = new DriverService(driverRepository);
        DriverAllocator allocator = new DriverAllocator(driverRepository, new NearestDriverStrategy(),
                new RideProperties(RADIUS_KM, MatchingStrategyType.NEAREST));
        rides = new RideService(users, drivers, coupons, allocator, new NoSurge(),
                PricingFixtures.fareCalculator(), new RideRepository(), clock);
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
