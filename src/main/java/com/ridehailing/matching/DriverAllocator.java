package com.ridehailing.matching;

import com.ridehailing.config.RideProperties;
import com.ridehailing.domain.CarType;
import com.ridehailing.domain.Driver;
import com.ridehailing.domain.Location;
import com.ridehailing.repository.DriverRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Finds and atomically claims a driver for a booking: requested car type first, then its free
 * upgrade (Hatchback -> Sedan). Ordering among eligible drivers is delegated to the configured
 * {@link DriverMatchingStrategy}, so switching strategies never touches booking logic.
 */
@Component
public class DriverAllocator {

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
            carType = carType.get().freeUpgrade();
        }
        return Optional.empty();
    }

    private Optional<Driver> claimFirstAvailable(CarType carType, Location pickup) {
        var candidates = drivers.findAvailableWithin(carType, pickup, searchRadiusKm);
        // Another booking may claim a candidate between the search and here; tryAssign() is the
        // atomic check, and on losing the race we simply move to the next-best driver.
        return matchingStrategy.rank(candidates, pickup).stream()
                .filter(Driver::tryAssign)
                .findFirst();
    }
}
