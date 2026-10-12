package com.ridehailing.matching;

import com.ridehailing.config.RideProperties;
import com.ridehailing.domain.CarType;
import com.ridehailing.domain.Driver;
import com.ridehailing.domain.Location;
import com.ridehailing.repository.DriverRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Finds and atomically claims a driver for a booking: requested car type first, then its free
 * upgrade (Hatchback -> Sedan). Ordering among eligible drivers is delegated to the configured
 * {@link DriverMatchingStrategy}, so switching strategies never touches booking logic.
 */
@Component
public class DriverAllocator {

    private static final Logger log = LoggerFactory.getLogger(DriverAllocator.class);

    private final DriverRepository drivers;
    private final DriverMatchingStrategy matchingStrategy;
    private final double searchRadiusKm;

    public DriverAllocator(DriverRepository drivers, DriverMatchingStrategy matchingStrategy,
                           RideProperties properties) {
        this.drivers = drivers;
        this.matchingStrategy = matchingStrategy;
        this.searchRadiusKm = properties.searchRadiusKm();
    }

    /** @return the claimed driver (already marked ON_RIDE), or empty if nobody is available */
    public Optional<Driver> claim(CarType requested, Location pickup) {
        Optional<CarType> carType = Optional.of(requested);
        while (carType.isPresent()) {
            Optional<Driver> claimed = claimFirstAvailable(carType.get(), pickup);
            if (claimed.isPresent()) {
                return claimed;
            }
            Optional<CarType> upgrade = carType.get().freeUpgrade();
            upgrade.ifPresent(next -> log.info("No {} available near {}; trying free upgrade to {}", requested, pickup, next));
            carType = upgrade;
        }
        return Optional.empty();
    }

    private Optional<Driver> claimFirstAvailable(CarType carType, Location pickup) {
        var candidates = drivers.findAvailableWithin(carType, pickup, searchRadiusKm);
        log.debug("{} available {} driver(s) within {} km of {}", candidates.size(), carType, searchRadiusKm, pickup);
        // Another booking may claim a candidate between the search and here; tryAssign() is the
        // atomic check, and on losing the race we simply move to the next-best driver.
        for (Driver driver : matchingStrategy.rank(candidates, pickup)) {
            if (driver.tryAssign()) {
                return Optional.of(driver);
            }
            log.debug("Driver {} was claimed by a concurrent booking; trying the next candidate", driver.id());
        }
        return Optional.empty();
    }
}
